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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
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
import net.imglib2.view.Views;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class View {

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		@Option(name = "-i", aliases = {"--container"}, required = true, usage = "container path, e.g. /nrs/flyem/data/tmp/Z0115-22.n5")
		private final List<String> containerPaths = null;

		@Option(name = "-d", aliases = {"--datasets"}, required = true, usage = "comma separated list of datasets, e.g. '/slab-26,slab-27'")
		final List<String> datasetLists = null;

		private boolean parsedSuccessfully = false;

		private final HashMap<N5Reader, String[]> sourcePaths = new HashMap<>();

		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);
				for (int i = 0; i < containerPaths.size(); ++i) {
					final String containerPath = containerPaths.get(i);
					final N5Reader n5;
					if (Files.isRegularFile(Paths.get(containerPath)))
						n5 = new N5HDF5Reader(HDF5Factory.openForReading(containerPath), 64);
					else
						n5 = new N5FSReader(containerPath);

					sourcePaths.put(n5, datasetLists.get(i).split(",\\s*"));
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
		 * @return the source path tuples
		 */
		public HashMap<N5Reader, String[]> getSourcePaths() {
			return sourcePaths;
		}

		/**
		 * @param parsedSuccessfully the parsedSuccessfully to set
		 */
		public void setParsedSuccessfully(final boolean parsedSuccessfully) {
			this.parsedSuccessfully = parsedSuccessfully;
		}
	}

	public static final void main(final String... args) throws IOException, InterruptedException, ExecutionException {

		final Options options = new Options(args);

		if (!options.parsedSuccessfully)
			return;


		final int numProc = Runtime.getRuntime().availableProcessors();
		final SharedQueue queue = new SharedQueue(Math.max(1, numProc / 2));
		BdvStackSource<?> bdv = null;

		for (final Entry<N5Reader, String[]> entry : options.getSourcePaths().entrySet()) {

			System.out.println(entry.getKey() + " : " + Arrays.toString(entry.getValue()));
			final N5Reader n5 = entry.getKey();
			for (final String datasetName : entry.getValue()) {

				RandomAccessibleInterval<?> source = N5Utils.openVolatile(n5, datasetName);
				for (int d = 0; d < source.numDimensions();)
					if (source.dimension(d) == 1)
						source = Views.hyperSlice(source, d, 0);
					else ++d;
				BdvOptions bdvOptions = source.numDimensions() == 2 ? Bdv.options().is2D() : Bdv.options();
				bdv = BdvFunctions.show(
						VolatileViews.wrapAsVolatile(
								source,
								queue,
								new CacheHints(LoadingStrategy.VOLATILE, 0, true)),
						datasetName,
						bdv == null ? bdvOptions : bdvOptions.addTo(bdv));
			}
		}
	}
}
