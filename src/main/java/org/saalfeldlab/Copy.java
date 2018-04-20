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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.janelia.saalfeldlab.n5.Bzip2Compression;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.Lz4Compression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.XzCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.saalfeldlab.N5Factory.N5BackendType;
import org.saalfeldlab.N5Factory.N5Options;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.util.Pair;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class Copy {

	protected final N5Reader n5Reader;
	protected final N5BackendType n5ReaderBackend;
	protected final N5Writer n5Writer;
	protected final N5BackendType n5WriterBackend;
	protected final int[] blockSize;
	protected final Compression compression;
	protected final int numProc;

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		@Option(name = "-i", aliases = { "--inputContainer" }, required = true, usage = "container path, e.g. /nrs/flyem/data/tmp/Z0115-22.h5")
		private String inputContainerPath = null;

		@Option(name = "-o", aliases = { "--outputContainer" }, required = true, usage = "container path, e.g. /nrs/flyem/data/tmp/Z0115-22.n5")
		private String outputContainerPath = null;

		@Option(name = "-d", aliases = { "--group" }, usage = "group or dataset name, e.g. /volumes/raw")
		private List<String> groupNames = null;

		@Option(name = "-b", aliases = { "--blockSize" }, usage = "override blockSize of input datasets, e.g. 256,256,26")
		private String blockSizeString = null;

		@Option(name = "-c", aliases = { "--compression" }, usage = "override compression type of input datasets, e.g. gzip")
		private String compressionString = "";

		private boolean parsedSuccessfully = false;
		private Pair<N5Reader, N5BackendType> n5ReaderAndBackend;
		private Pair<N5Writer, N5BackendType> n5WriterAndBackend;
		private int[] blockSize;
		private Compression compression;

		protected static final int[] parseCSIntArray(final String csv) {

			final String[] stringValues = csv.split(",\\s*");
			final int[] array = new int[stringValues.length];
			try {
				for (int i = 0; i < array.length; ++i)
					array[i] = Integer.parseInt(stringValues[i]);
			} catch (final NumberFormatException e) {
				e.printStackTrace(System.err);
				return null;
			}
			return array;
		}

		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);

				if (blockSizeString != null)
					blockSize = parseCSIntArray(blockSizeString);

				if (compressionString == null)
					compression = null;
				else {
					switch (compressionString.toLowerCase()) {
					case "raw":
						compression = new RawCompression();
						break;
					case "bzip2":
						compression = new Bzip2Compression();
						break;
					case "lz4":
						compression = new Lz4Compression();
						break;
					case "xz":
						compression = new XzCompression();
						break;
					case "gzip":
						compression = new GzipCompression();
						break;
					default:
						compression = null;
					}
				}

				n5ReaderAndBackend = N5Factory.createN5Reader(new N5Options(inputContainerPath, blockSize, compression));
				n5WriterAndBackend = N5Factory.createN5Writer(new N5Options(outputContainerPath, blockSize, compression));

				parsedSuccessfully = true;
			} catch (final Exception e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}

		public N5Reader getReader() {

			return n5ReaderAndBackend.getA();
		}

		public N5BackendType getReaderBackend() {

			return n5ReaderAndBackend.getB();
		}

		public N5Writer getWriter() {

			return n5WriterAndBackend.getA();
		}

		public N5BackendType getWriterBackend() {

			return n5WriterAndBackend.getB();
		}

		public int[] getBlockSize() {

			return blockSize;
		}

		public Compression getCompression() {

			return compression;
		}

		public List<String> getGroupNames() {

			return groupNames;
		}

		public boolean isParsedSuccessfully() {

			return parsedSuccessfully;
		}
	}

	public Copy(final Options options) {

		n5Reader = options.getReader();
		n5ReaderBackend = options.getReaderBackend();
		n5Writer = options.getWriter();
		n5WriterBackend = options.getWriterBackend();
		blockSize = options.getBlockSize();
		compression = options.getCompression();
		numProc = Runtime.getRuntime().availableProcessors();
	}

	protected static void reorder(final long[] array) {

		long a;
		final int max = array.length - 1;
		for (int i = (max - 1) / 2; i >= 0; --i) {
			final int j = max - i;
			a = array[i];
			array[i] = array[j];
			array[j] = a;
		}
	}

	protected static void reorder(final double[] array) {

		double a;
		final int max = array.length - 1;
		for (int i = (max - 1) / 2; i >= 0; --i) {
			final int j = max - i;
			a = array[i];
			array[i] = array[j];
			array[j] = a;
		}
	}

	protected void reorderIfNecessary(final double[] array) {

		if ((n5ReaderBackend == N5BackendType.HDF5) != (n5WriterBackend == N5BackendType.HDF5))
			reorder(array);
	}

	protected void reorderIfNecessary(final long[] array) {

		if ((n5ReaderBackend == N5BackendType.HDF5) != (n5WriterBackend == N5BackendType.HDF5))
			reorder(array);
	}

	protected <T extends NativeType<T>> void copyDataset(final String datasetName) throws IOException, InterruptedException, ExecutionException {

		System.out.println(datasetName);

		final DatasetAttributes datasetAttributes = n5Reader.getDatasetAttributes(datasetName);

		final RandomAccessibleInterval<T> dataset;
		try {
			dataset = N5Utils.open(n5Reader, datasetName);
		} catch (final Exception e) {
			e.printStackTrace(System.err);
			return;
		}

		if (n5WriterBackend == N5BackendType.HDF5) {
			N5Utils.save(
					dataset,
					n5Writer,
					datasetName,
					blockSize == null || blockSize.length != dataset.numDimensions() ? datasetAttributes.getBlockSize() : blockSize,
					compression == null ? datasetAttributes.getCompression() : compression);
		} else {
			final ExecutorService exec = Executors.newFixedThreadPool(numProc);
			N5Utils.save(
					dataset,
					n5Writer,
					datasetName,
					blockSize == null || blockSize.length != dataset.numDimensions() ? datasetAttributes.getBlockSize() : blockSize,
					compression == null ? datasetAttributes.getCompression() : compression,
					exec);
			exec.shutdown();
		}

		copyAttributes(datasetName);
	}

	protected void copyAttributes(final String groupName)
			throws IOException {

		System.out.println("  attributes:");

		final Map<String, Class<?>> attributes = n5Reader.listAttributes(groupName);

		final DatasetAttributes datasetAttributes = n5Reader.datasetExists(groupName) ? n5Reader.getDatasetAttributes(groupName) : null;
		final Set<String> datasetAttributeKeys = datasetAttributes == null ? new HashSet<>() : datasetAttributes.asMap().keySet();

		attributes.forEach((key, clazz) -> {

			if (datasetAttributeKeys.contains(key)) {
				System.out.println("    skipping dataset attribute " + key + " : " + clazz);
			} else {
				System.out.println("    " + key + " : " + clazz);

				try {
					n5Writer.setAttribute(groupName, key, n5Reader.getAttribute(groupName, key, clazz));
				} catch (final IOException e) {
					e.printStackTrace(System.err);
				}
			}
		});
	}

	protected <T extends NativeType<T>> void copyGroup(final String groupName) throws IOException, InterruptedException, ExecutionException {

		System.out.println(groupName);

		n5Writer.createGroup(groupName);
		copyAttributes(groupName);

		final String[] subGroupNames = n5Reader.list(groupName);
		for (final String subGroupName : subGroupNames) {
			if (n5Reader.datasetExists(groupName + "/" + subGroupName))
				copyDataset(groupName + "/" + subGroupName);
			else
				copyGroup(groupName + "/" + subGroupName);
		}
	}

	public static void main(final String... args) throws IOException, InterruptedException, ExecutionException {

		final Options options = new Options(args);

		if (!options.parsedSuccessfully)
			return;

		final Copy copy = new Copy(options);

		final List<String> groupNames = options.getGroupNames();

		if (groupNames == null)
			copy.copyGroup("");
		else {
			for (final String groupName : groupNames)
				if (copy.n5Reader.exists(groupName)) {
					if (copy.n5Reader.datasetExists(groupName))
						copy.copyDataset(groupName);
					else
						copy.copyGroup(groupName);
				}
		}
	}
}
