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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.saalfeldlab.N5Factory.N5Options;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.converter.Converters;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.AbstractVolatileNativeRealType;
import net.imglib2.type.volatiles.VolatileDoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class View {

	protected static class ReaderInfo {

		public final N5Reader n5;
		public final String[] groupNames;
		public final double[][] resolutions;
		public final double[][] contrastRanges;

		public ReaderInfo(
				final N5Reader n5,
				final String[] groupNames,
				final double[][] resolutions,
				final double[][] contrastRanges) {

			this.n5 = n5;
			this.groupNames = groupNames;
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

		@Option(name = "-i", aliases = {"--container"}, required = true, usage = "container paths, e.g. -i $HOME/fib19.n5 -i /nrs/flyem ...")
		private final List<String> containerPaths = null;

		@Option(name = "-d", aliases = {"--datasets"}, required = true, usage = "comma separated list of datasets, one list per container, e.g. -d '/slab-26,slab-27' -d '/volumes/raw' ...")
		final List<String> groupLists = null;

		@Option(name = "-r", aliases = {"--resolution"}, usage = "comma separated list of scale factors, one per dataset or all following the last, e.g. -r '4,4,40'")
		final List<String> resolutionStrings = null;

		@Option(name = "-c", aliases = {"--contrast"}, usage = "comma separated contrast range, one per dataset or all following the last, e.g. -c '0,255'")
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
					final N5Reader n5 = N5Factory.createN5Reader(new N5Options(containerPath, new int[] {64}, null)).getA();
					final String[] groups = groupLists.get(i).split(",\\s*");
					final double[][] resolutions = new double[groups.length][];
					final double[][] contrastRanges = new double[groups.length][];
					for (int l = 0; l < groups.length; ++l) {
						if (resolutionStrings != null && j < resolutionStrings.size())
							resolution = parseCSDoubleArray(resolutionStrings.get(j));
						if (contrastStrings != null && k < contrastStrings.size())
							contrast = parseCSDoubleArray(contrastStrings.get(k));

						resolutions[l] = resolution.clone();
						contrastRanges[l] = contrast.clone();
						++j; ++k;
					}

					readerInfos.add(new ReaderInfo(n5, groups, resolutions, contrastRanges));
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

	private static final double[] rs = new double[]{1, 1, 0, 0, 0, 1, 1};
	private static final double[] gs = new double[]{0, 1, 1, 1, 0, 0, 0};
	private static final double[] bs = new double[]{0, 0, 0, 1, 1, 1, 0};

	final static private double goldenRatio = 1.0 / (0.5 * Math.sqrt(5) + 0.5);

	private static final double getDouble(final long id) {

		final double x = id * goldenRatio;
		return x - (long)Math.floor(x);
	}

	private static final int interpolate(final double[] xs, final int k, final int l, final double u, final double v) {

		return (int)((v * xs[k] + u * xs[l]) * 255.0 + 0.5);
	}

	private static final int argb(final int r, final int g, final int b, final int alpha) {

		return (((r << 8) | g) << 8) | b | alpha;
	}

	private static final int argb(final long id) {

		double x = getDouble(id);
		x *= 6.0;
		final int k = (int)x;
		final int l = k + 1;
		final double u = x - k;
		final double v = 1.0 - u;

		final int r = interpolate( rs, k, l, u, v );
		final int g = interpolate( gs, k, l, u, v );
		final int b = interpolate( bs, k, l, u, v );

		return argb( r, g, b, 0xff );
	}



	@SuppressWarnings( "unchecked" )
	public static final void main(final String... args) throws IOException, InterruptedException, ExecutionException {

		final Options options = new Options(args);

		if (!options.parsedSuccessfully)
			return;

		final int numProc = Runtime.getRuntime().availableProcessors();
		final SharedQueue queue = new SharedQueue(Math.min(8, Math.max(1, numProc / 2)));
		BdvStackSource<?> bdv = null;

		int id = 0;
		for (final ReaderInfo entry : options.getReaderInfos()) {

			final N5Reader n5 = entry.n5;
			for (int i = 0; i < entry.groupNames.length; ++i) {

				final String groupName = entry.groupNames[i];
				final double[] resolution = entry.resolutions[i];
				final double[] contrast = entry.contrastRanges[i];

				System.out.println(n5 + " : " + groupName + ", " + Arrays.toString(resolution) + ", " + Arrays.toString(contrast));

				final Pair<RandomAccessibleInterval<NativeType>[], double[][]> n5Sources;
				int n;
				if (n5.datasetExists(groupName)) {
					// this works for javac openjdk 8
					final RandomAccessibleInterval<NativeType> source = (RandomAccessibleInterval)N5Utils.openVolatile(n5, groupName);
					n = source.numDimensions();
					final double[] scale = new double[n];
					Arrays.fill(scale, 1);
					n5Sources = new ValuePair<>(new RandomAccessibleInterval[] {source}, new double[][]{scale});
				}
				else {
					n5Sources = N5Utils.openMipmaps(n5, groupName, true);
					n = n5Sources.getA()[0].numDimensions();
				}

				/* make volatile */
				final RandomAccessibleInterval<NativeType>[] ras = n5Sources.getA();
				final RandomAccessibleInterval[] vras = new RandomAccessibleInterval[ras.length];
				Arrays.setAll(vras, k ->
					VolatileViews.wrapAsVolatile(
							n5Sources.getA()[k],
							queue,
							new CacheHints(LoadingStrategy.VOLATILE, 0, true)));

				/* remove 1-size dimensions */
				final double[][] scales = n5Sources.getB();
				for (int d = 0; d < n;) {
					if (ras[0].dimension(d) == 1) {
						--n;
						for (int k = 0; k < vras.length; ++k) {
							vras[k] = Views.hyperSlice(vras[k], d, 0);
							final double[] oldScale = scales[k];
							scales[k] = Arrays.copyOf(oldScale, n);
							System.arraycopy(oldScale, d + 1, scales[k], d, scales[k].length - d);
						}
					} else ++d;
				}

				final BdvOptions bdvOptions = n == 2 ? Bdv.options().is2D() : Bdv.options();

				final RandomAccessibleInterval<VolatileDoubleType>[] convertedSources = new RandomAccessibleInterval[n5Sources.getA().length];
				for (int k = 0; k < vras.length; ++k) {
					convertedSources[k] = Converters.convert(
							(RandomAccessibleInterval<AbstractVolatileNativeRealType<?, ?>>)vras[k],
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
					final double[] scale = n5Sources.getB()[k];
					Arrays.setAll(scale, j -> scale[j] * resolution[j]);
				}

				final RandomAccessibleIntervalMipmapSource<VolatileDoubleType> mipmapSource =
						new RandomAccessibleIntervalMipmapSource<>(
								convertedSources,
								new VolatileDoubleType(),
								n5Sources.getB(),
								new FinalVoxelDimensions("px", resolution),
								groupName);

				bdv = BdvFunctions.show(
						mipmapSource,
						bdv == null ? bdvOptions : bdvOptions.addTo(bdv));
				bdv.setDisplayRange(0, 1000);
				bdv.setColor(new ARGBType(argb(id++)));
			}

			if (id == 1)
				bdv.setColor(new ARGBType(0xffffffff));
		}
	}
}
