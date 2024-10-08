/**
 *                         THE CRAPL v0 BETA 1
 *
 *
 * 0. Information about the CRAPL
 *
 * If you have questions or concerns about the CRAPL, or you need more
 * information about this license, please contact:
 *
 *    Matthew Might
 *    http://matt.might.net/
 *
 *
 * I. Preamble
 *
 * Science thrives on openness.
 *
 * In modern science, it is often infeasible to replicate claims without
 * access to the software underlying those claims.
 *
 * Let's all be honest: when scientists write code, aesthetics and
 * software engineering principles take a back seat to having running,
 * working code before a deadline.
 *
 * So, let's release the ugly.  And, let's be proud of that.
 *
 *
 * II. Definitions
 *
 * 1. "This License" refers to version 0 beta 1 of the Community
 *     Research and Academic Programming License (the CRAPL).
 *
 * 2. "The Program" refers to the medley of source code, shell scripts,
 *     executables, objects, libraries and build files supplied to You,
 *     or these files as modified by You.
 *
 *    [Any appearance of design in the Program is purely coincidental and
 *     should not in any way be mistaken for evidence of thoughtful
 *     software construction.]
 *
 * 3. "You" refers to the person or persons brave and daft enough to use
 *     the Program.
 *
 * 4. "The Documentation" refers to the Program.
 *
 * 5. "The Author" probably refers to the caffeine-addled graduate
 *     student that got the Program to work moments before a submission
 *     deadline.
 *
 *
 * III. Terms
 *
 * 1. By reading this sentence, You have agreed to the terms and
 *    conditions of this License.
 *
 * 2. If the Program shows any evidence of having been properly tested
 *    or verified, You will disregard this evidence.
 *
 * 3. You agree to hold the Author free from shame, embarrassment or
 *    ridicule for any hacks, kludges or leaps of faith found within the
 *    Program.
 *
 * 4. You recognize that any request for support for the Program will be
 *    discarded with extreme prejudice.
 *
 * 5. The Author reserves all rights to the Program, except for any
 *    rights granted under any additional licenses attached to the
 *    Program.
 *
 *
 * IV. Permissions
 *
 * 1. You are permitted to use the Program to validate published
 *    scientific claims.
 *
 * 2. You are permitted to use the Program to validate scientific claims
 *    submitted for peer review, under the condition that You keep
 *    modifications to the Program confidential until those claims have
 *    been published.
 *
 * 3. You are permitted to use and/or modify the Program for the
 *    validation of novel scientific claims if You make a good-faith
 *    attempt to notify the Author of Your work and Your claims prior to
 *    submission for publication.
 *
 * 4. If You publicly release any claims or data that were supported or
 *    generated by the Program or a modification thereof, in whole or in
 *    part, You will release any inputs supplied to the Program and any
 *    modifications You made to the Progam.  This License will be in
 *    effect for the modified program.
 *
 *
 * V. Disclaimer of Warranty
 *
 * THERE IS NO WARRANTY FOR THE PROGRAM, TO THE EXTENT PERMITTED BY
 * APPLICABLE LAW. EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT
 * HOLDERS AND/OR OTHER PARTIES PROVIDE THE PROGRAM "AS IS" WITHOUT
 * WARRANTY OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE. THE ENTIRE RISK AS TO THE QUALITY AND
 * PERFORMANCE OF THE PROGRAM IS WITH YOU. SHOULD THE PROGRAM PROVE
 * DEFECTIVE, YOU ASSUME THE COST OF ALL NECESSARY SERVICING, REPAIR OR
 * CORRECTION.
 *
 *
 * VI. Limitation of Liability
 *
 * IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN WRITING
 * WILL ANY COPYRIGHT HOLDER, OR ANY OTHER PARTY WHO MODIFIES AND/OR
 * CONVEYS THE PROGRAM AS PERMITTED ABOVE, BE LIABLE TO YOU FOR DAMAGES,
 * INCLUDING ANY GENERAL, SPECIAL, INCIDENTAL OR CONSEQUENTIAL DAMAGES
 * ARISING OUT OF THE USE OR INABILITY TO USE THE PROGRAM (INCLUDING BUT
 * NOT LIMITED TO LOSS OF DATA OR DATA BEING RENDERED INACCURATE OR
 * LOSSES SUSTAINED BY YOU OR THIRD PARTIES OR A FAILURE OF THE PROGRAM
 * TO OPERATE WITH ANY OTHER PROGRAMS), EVEN IF SUCH HOLDER OR OTHER
 * PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 *
 */
package org.janelia.saalfeldlab;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.janelia.saalfeldlab.n5.Bzip2Compression;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.Lz4Compression;
import org.janelia.saalfeldlab.n5.N5Exception;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.XzCompression;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.jpeg.JPEGCompression;
import org.janelia.saalfeldlab.n5.universe.N5Factory;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * Application to copy/ re-block/ re-compress a group from one N5 container into another.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class Copy implements Callable<Void> {

	protected N5Reader n5Reader;
	protected N5Writer n5Writer;
	protected int[] blockSize;
	protected Compression compression;
	protected final int numProc = Runtime.getRuntime().availableProcessors();

	@Option(names = {"-i", "--inputContainer" }, required = true, description = "container path, e.g. /nrs/flyem/data/tmp/Z0115-22.h5")
	private String inputContainerPath = null;

	@Option(names = {"-o", "--outputContainer" }, required = true, description = "container path, e.g. /nrs/flyem/data/tmp/Z0115-22.n5")
	private String outputContainerPath = null;

	@Option(names = {"-d", "--group" }, description = "group or dataset name, e.g. /volumes/raw")
	private List<String> groupNames = null;

	@Option(names = {"-b", "--blockSize" }, description = "override blockSize of input datasets, e.g. 256,256,26")
	private String blockSizeString = null;

	@Option(names = {"-c", "--compression" }, description = "override compression type of input N5 datasets, e.g. gzip (HDF5 inputs are copied without compression by default, in this case this option sets the output compression)")
	private String compressionString = "";

	@Option(names = {"-p", "--compressionParameter" }, description = "specify a compression parameter, e.g. 5 as the compression level for gzip or 1024 as the block size for bzip2")
	private int compressionParameter = -1;


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

	public static final void main(final String... args) {

		System.exit(new CommandLine(new Copy()).execute(args));
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

		if ((n5Reader instanceof N5HDF5Reader) != (n5Writer instanceof N5HDF5Writer))
			reorder(array);
	}

	protected void reorderIfNecessary(final long[] array) {

		if ((n5Reader instanceof N5HDF5Reader) != (n5Writer instanceof N5HDF5Writer))
			reorder(array);
	}

	protected <T extends NativeType<T>> void copyDataset(final String datasetName) throws InterruptedException, ExecutionException {

		System.out.println(datasetName);

		final DatasetAttributes datasetAttributes = n5Reader.getDatasetAttributes(datasetName);

		final RandomAccessibleInterval<T> dataset;
		try {
			dataset = N5Utils.open(n5Reader, datasetName);
		} catch (final Exception e) {
			e.printStackTrace(System.err);
			return;
		}

		if (n5Writer instanceof N5HDF5Writer) {
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

	protected void copyAttributes(final String groupName) {

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
				} catch (final N5Exception e) {
					e.printStackTrace(System.err);
				}
			}
		});
	}

	protected <T extends NativeType<T>> void copyGroup(final String groupName) throws InterruptedException, ExecutionException {

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

	@Override
	public Void call() throws InterruptedException, ExecutionException {

		blockSize = blockSizeString == null ? null : parseCSIntArray(blockSizeString);

		if (compressionString == null)
			compression = null;
		else {
			switch (compressionString.toLowerCase()) {
			case "raw":
				compression = new RawCompression();
				break;
			case "bzip2":
				compression = compressionParameter > 0 ? new Bzip2Compression(compressionParameter) : new Bzip2Compression();
				break;
			case "lz4":
				compression = compressionParameter > 0 ? new Lz4Compression(compressionParameter) : new Lz4Compression();
				break;
			case "xz":
				compression = compressionParameter >= 0 ? new XzCompression(compressionParameter) : new XzCompression();
				break;
			case "gzip":
				compression = compressionParameter > 0 ? new GzipCompression(compressionParameter) : new GzipCompression();
				break;
			case "zip":
				compression = compressionParameter > 0 ? new GzipCompression(compressionParameter, true) : new GzipCompression(-1, true);
				break;
			case "jpeg":
				compression = compressionParameter > 0 ? new JPEGCompression(compressionParameter) : new JPEGCompression();
				break;
			default:
				compression = null;
			}
		}

		final N5Factory n5Factory = new N5Factory()
				.hdf5DefaultBlockSize(blockSize)
				.zarrDimensionSeparator(".")
				.zarrMapN5Attributes(false)
				.zarrMergeAttributes(false);

		n5Reader = n5Factory.openReader(inputContainerPath);
		n5Writer = n5Factory.openWriter(outputContainerPath);

		if (groupNames == null)
			copyGroup("");
		else {
			for (final String groupName : groupNames)
				if (n5Reader.exists(groupName)) {
					if (n5Reader.datasetExists(groupName))
						copyDataset(groupName);
					else
						copyGroup(groupName);
				}
		}

		return null;
	}
}
