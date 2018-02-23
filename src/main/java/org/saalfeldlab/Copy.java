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
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.XzCompression;
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

	protected final N5Reader n5Reader;
	protected final N5Writer n5Writer;
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

		@Option(name = "-c", aliases = { "--compression" }, usage = "override compression type of input datasets, e.g. 256,256,26")
		private String compressionString = "";

		private boolean parsedSuccessfully = false;
		private N5Reader n5Reader;
		private N5Writer n5Writer;
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

				final boolean h5Input = Files.isRegularFile(Paths.get(inputContainerPath));
				final boolean h5Output = outputContainerPath.endsWith(".h5") || outputContainerPath.endsWith(".hdf5") || outputContainerPath.endsWith(".hdf");

				if (blockSizeString != null)
					blockSize = parseCSIntArray(blockSizeString);

				if (h5Input)
					n5Reader = new N5HDF5Reader(HDF5Factory.openForReading(inputContainerPath), blockSize);
				else
					n5Reader = new N5FSReader(inputContainerPath);

				if (h5Output)
					n5Writer = new N5HDF5Writer(HDF5Factory.open(outputContainerPath), blockSize);
				else
					n5Writer = new N5FSWriter(outputContainerPath);

				if (compressionString == null)
					compression = null;
				else {
					switch (compressionString) {
					case "raw":
					case "RAW":
					case "Raw":
						compression = new RawCompression();
						break;
					case "bzip2":
					case "BZIP2":
					case "Bzip2":
						compression = new Bzip2Compression();
						break;
					case "lz4":
					case "LZ4":
					case "Lz4":
						compression = new Lz4Compression();
						break;
					case "xz":
					case "XZ":
					case "Xz":
						compression = new XzCompression();
						break;
					case "gzip":
					case "GZIP":
					case "Gzip":
						compression = new GzipCompression();
						break;
					default:
						compression = null;
					}
				}

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
		n5Writer = options.getWriter();
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

	protected void reorderIfNecessary(
			final double[] array) {

		if (!(n5Reader.getClass().isInstance(n5Writer) || n5Writer.getClass().isInstance(n5Reader)))
			reorder(array);
	}

	protected void reorderIfNecessary(
			final long[] array) {

		if (!(n5Reader.getClass().isInstance(n5Writer) || n5Writer.getClass().isInstance(n5Reader)))
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

		if (n5Writer instanceof N5HDF5Writer)
			N5Utils.save(
					dataset,
					n5Writer,
					datasetName,
					blockSize == null || blockSize.length != dataset.numDimensions() ? datasetAttributes.getBlockSize() : blockSize,
					compression == null ? datasetAttributes.getCompression() : compression);
		else {
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
