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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class Copy {

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		@Option(name = "-i", aliases = {"--inputContainer"}, required = true, usage = "container path, e.g. /nrs/flyem/data/tmp/Z0115-22.h5")
		private final String inputContainerPath = null;

		@Option(name = "-o", aliases = {"--outputContainer"}, required = true, usage = "container path, e.g. /nrs/flyem/data/tmp/Z0115-22.n5")
		private final String outputContainerPath = null;

		private boolean parsedSuccessfully = false;
		private N5Reader n5Reader;
		private N5Writer n5Writer;

		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);

				if (Files.isRegularFile(Paths.get(inputContainerPath)))
					n5Reader = new N5HDF5Reader(HDF5Factory.openForReading(inputContainerPath), 64);
				else
					n5Reader = new N5FSReader(inputContainerPath);

				if (Files.isRegularFile(Paths.get(outputContainerPath)))
					n5Writer = new N5HDF5Writer(HDF5Factory.open(outputContainerPath), 64);
				else
					n5Writer = new N5FSWriter(outputContainerPath);

				parsedSuccessfully = true;
			} catch (final Exception e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}

		public N5Reader getReader() {

			return n5Reader;
		}

		public N5Writer getWriter() {

			return n5Writer;
		}

		public boolean isParsedSuccessfully() {

			return parsedSuccessfully;
		}
	}

	private static void reorder(final long[] array) {

		long a;
		final int max = array.length - 1;
		for (int i = (max - 1) / 2; i >= 0; --i) {
			final int j = max - i;
			a = array[i];
			array[i] = array[j];
			array[j] = a;
		}
	}

	private static void reorder(final double[] array) {

		double a;
		final int max = array.length - 1;
		for (int i = (max - 1) / 2; i >= 0; --i) {
			final int j = max - i;
			a = array[i];
			array[i] = array[j];
			array[j] = a;
		}
	}

	private static void reorderIfNecessary(
			final N5Reader n5Reader,
			final N5Writer n5Writer,
			final double[] array) {

		if (!(n5Reader.getClass().isInstance(n5Writer) ||  n5Writer.getClass().isInstance(n5Reader)))
			reorder(array);
	}

	private static void reorderIfNecessary(
			final N5Reader n5Reader,
			final N5Writer n5Writer,
			final long[] array) {

		if (!(n5Reader.getClass().isInstance(n5Writer) ||  n5Writer.getClass().isInstance(n5Reader)))
			reorder(array);
	}

	private static <T extends NativeType<T>> void copyDataset(
			final N5Reader n5Reader,
			final N5Writer n5Writer,
			final String datasetName,
			final int numProcessors) throws IOException, InterruptedException, ExecutionException {

		final DatasetAttributes attributes = n5Reader.getDatasetAttributes(datasetName);

		final RandomAccessibleInterval<T> dataset;
		try {
			dataset = N5Utils.open(n5Reader, datasetName);
		} catch (final Exception e) {
			e.printStackTrace(System.err);
			return;
		}

		if (n5Writer instanceof N5HDF5Writer)
			N5Utils.save(dataset, n5Writer, datasetName, attributes.getBlockSize(), attributes.getCompressionType());
		else {
			final ExecutorService exec = Executors.newFixedThreadPool(numProcessors);
			N5Utils.save(dataset, n5Writer, datasetName, attributes.getBlockSize(), attributes.getCompressionType(), exec);
			exec.shutdown();
		}

		final double[] resolution = n5Reader.getAttribute(datasetName, "resolution", double[].class);
		if (resolution != null)
			reorderIfNecessary(n5Reader, n5Writer, resolution);
		n5Writer.setAttribute(datasetName, "resolution", resolution);

		final double[] offset = n5Reader.getAttribute(datasetName, "offset", double[].class);
		if (offset != null)
			reorderIfNecessary(n5Reader, n5Writer, offset);
		n5Writer.setAttribute(datasetName, "offset", offset);
	}

	private static <T extends NativeType<T>> void copyGroup(
			final N5Reader n5Reader,
			final N5Writer n5Writer,
			final String groupName,
			final int numProcessors) throws IOException, InterruptedException, ExecutionException {

		System.out.println( "Copy group " + groupName );

		n5Writer.createGroup(groupName);
		final String[] subGroupNames = n5Reader.list(groupName);
		for (final String subGroupName : subGroupNames) {
			if (n5Reader.datasetExists(groupName + "/" + subGroupName))
				copyDataset(n5Reader, n5Writer, groupName + "/" + subGroupName, numProcessors);
			else
				copyGroup(n5Reader, n5Writer, groupName + "/" + subGroupName, numProcessors);
		}
	}

	public static final void main(final String... args) throws IOException, InterruptedException, ExecutionException {

		final Options options = new Options(args);

		if (!options.parsedSuccessfully)
			return;

		final int numProc = Runtime.getRuntime().availableProcessors();

		N5Reader n5Reader = options.getReader();
		N5Writer n5Writer = options.getWriter();

		copyGroup(n5Reader, n5Writer, "/", numProc);
	}
}
