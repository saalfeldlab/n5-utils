/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.saalfeldlab;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileDoubleType;
import net.imglib2.view.Views;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class View {

	protected static class ReaderInfo {

		public final N5Reader n5;
		public final String[] datasetNames;
		public final double[][] resolutions;
		public final double[][] contrastRanges;

		public ReaderInfo(
				final N5Reader n5,
				final String[] datasetNames,
				final double[][] resolutions,
				final double[][] contrastRanges) {

			this.n5 = n5;
			this.datasetNames = datasetNames;
			this.resolutions = resolutions;
			this.contrastRanges = contrastRanges;
		}
	}

	public static class Options implements Serializable {

		protected static final boolean parseCSDoubleArray(final String csv, final double[] array) {

			final String[] stringValues = csv.split(",\\s*");
			if (stringValues.length != array.length)
				return false;
			try {
				for (int i = 0; i < array.length; ++i)
					array[i] = Double.parseDouble(stringValues[i]);
			} catch (final NumberFormatException e) {
				e.printStackTrace(System.err);
				return false;
			}
			return true;
		}

		protected static final double[] parseCSDoubleArray(final String csv) {

			final String[] stringValues = csv.split(",\\s*");
			final double[] array = new double[stringValues.length];
			try {
				for (int i = 0; i < array.length; ++i)
					array[i] = Double.parseDouble(stringValues[i]);
			} catch (final NumberFormatException e) {
				e.printStackTrace(System.err);
				return null;
			}
			return array;
		}

		@Option(name = "-i", aliases = {"--container"}, required = true, usage = "container path, e.g. /nrs/flyem/data/tmp/Z0115-22.n5")
		private final List<String> containerPaths = null;

		@Option(name = "-d", aliases = {"--datasets"}, required = true, usage = "comma separated list of datasets, e.g. '/slab-26,slab-27'")
		final List<String> datasetLists = null;

		@Option(name = "-r", aliases = {"--resolution"}, usage = "comma separated list of scale factors, e.g. '4,4,40'")
		final List<String> resolutionStrings = null;

		@Option(name = "-c", aliases = {"--contrast"}, usage = "comma separated contrast range, e.g. '0,255'")
		final List<String> contrastStrings = null;

		private boolean parsedSuccessfully = false;

		private final ArrayList<ReaderInfo> readerInfos = new ArrayList<>();

		public Options(final String[] args) throws NumberFormatException, IOException {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);
				double[] resolution = new double[]{1, 1, 1};
				double[] contrast = new double[]{0, 255};
				for (int i = 0, j = 0, k = 0; i < containerPaths.size(); ++i) {
					final String containerPath = containerPaths.get(i);
					final N5Reader n5;
					if (Files.isRegularFile(Paths.get(containerPath)))
						n5 = new N5HDF5Reader(HDF5Factory.openForReading(containerPath), 64);
					else
						n5 = new N5FSReader(containerPath);

					final String[] datasets = datasetLists.get(i).split(",\\s*");
					final double[][] resolutions = new double[datasets.length][];
					final double[][] contrastRanges = new double[datasets.length][];
					for (int l = 0; l < datasets.length; ++l) {
						if (resolutionStrings != null && j < resolutionStrings.size())
							resolution = parseCSDoubleArray(resolutionStrings.get(j));
						if (contrastStrings != null && k < contrastStrings.size())
							contrast = parseCSDoubleArray(contrastStrings.get(k));

						resolutions[l] = resolution.clone();
						contrastRanges[l] = contrast.clone();
						++j; ++k;
					}

					readerInfos.add(new ReaderInfo(n5, datasets, resolutions, contrastRanges));
				}
				parsedSuccessfully = true;
			} catch (final CmdLineException e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}

		public boolean isParsedSuccessfully() {

			return parsedSuccessfully;
		}

		/**
		 * @return the readers
		 */
		public List<ReaderInfo> getReaderInfos() {

			return readerInfos;
		}

		/**
		 * @param parsedSuccessfully the parsedSuccessfully to set
		 */
		public void setParsedSuccessfully(final boolean parsedSuccessfully) {
			this.parsedSuccessfully = parsedSuccessfully;
		}
	}

	@SuppressWarnings( "unchecked" )
	public static final void main(final String... args) throws IOException, InterruptedException, ExecutionException {

		final Options options = new Options(args);

		if (!options.parsedSuccessfully)
			return;

		final int numProc = Runtime.getRuntime().availableProcessors();
		final SharedQueue queue = new SharedQueue(Math.min(8, Math.max(1, numProc / 2)));
		BdvStackSource<?> bdv = null;

		for (final ReaderInfo entry : options.getReaderInfos()) {

			final N5Reader n5 = entry.n5;
			for (int i = 0; i < entry.datasetNames.length; ++i) {

				final String datasetName = entry.datasetNames[i];
				final double[] resolution = entry.resolutions[i];
				final double[] contrast = entry.contrastRanges[i];

				System.out.println(n5 + " : " + datasetName + ", " + Arrays.toString(resolution) + ", " + Arrays.toString(contrast));
				// this works for javac openjdk 8
				RandomAccessibleInterval<RealType<?>> source = (RandomAccessibleInterval)N5Utils.openVolatile(n5, datasetName);
				final AffineTransform3D sourceTransform = new AffineTransform3D();
				sourceTransform.set(
						resolution[0], 0, 0, 0,
						0, resolution[1], 0, 0,
						0, 0, resolution[2], 0);
				for (int d = 0; d < source.numDimensions();)
					if (source.dimension(d) == 1)
						source = Views.hyperSlice(source, d, 0);
					else ++d;
				final BdvOptions bdvOptions = source.numDimensions() == 2 ? Bdv.options().is2D().sourceTransform(sourceTransform) : Bdv.options().sourceTransform(sourceTransform);

				final RandomAccessibleInterval<VolatileDoubleType> convertedSource = Converters.convert(
						VolatileViews.wrapAsVolatile(
								source,
								queue,
								new CacheHints(LoadingStrategy.VOLATILE, 0, true)),
						(a, b) -> {
							b.setValid(a.isValid());
							if (b.isValid()) {
								double v = a.get().getRealDouble();
								v -= contrast[0];
								v /= contrast[1] - contrast[0];
								v *= 1000;
								b.setReal(v);
							}
						},
						new VolatileDoubleType());
				bdv = BdvFunctions.show(
						convertedSource,
						datasetName,
						bdv == null ? bdvOptions : bdvOptions.addTo(bdv));
				bdv.setDisplayRange(0, 1000);
			}
		}
	}
}
