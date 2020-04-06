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
package org.janelia.saalfeldlab.cosem;

import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.brightness.SetupAssignments;
import bdv.util.*;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.volatiles.AbstractVolatileNativeRealType;
import net.imglib2.type.volatiles.VolatileDoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.N5Factory;
import org.janelia.saalfeldlab.N5Factory.N5Options;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Reader.Version;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class ViewCosem<T extends NativeType<T> & NumericType<T>>  implements Callable<Void> {

    @Option(names = {"-i", "--container"}, required = true, description = "container path")
    private String containerPath = null;

    @Option(names = {"-r", "--raw"}, required = false, description = "path to raw data")
    private String rawDataPath = null;

    @Option(names = {"-t", "--threads"}, description = "number of rendering threads, e.g. -t 4 (default 3)")
    private int numRenderingThreads = 3;

    @Option(names = {"-s", "--scales"}, split = ",", description = "comma separated list of screen scales, e.g. -s 1.0,0.5,0.25 (default 1.0,0.75,0.5,0.25,0.125)")
    private double[] screenScales = new double[] {1.0, 0.5, 0.25, 0.125};

    @SuppressWarnings("unchecked")
    @Override
    public Void call() throws IOException {

        final int numProc = Runtime.getRuntime().availableProcessors();
        final SharedQueue queue = new SharedQueue(Math.min(8, Math.max(1, numProc / 2)));
        BdvStackSource<?> bdv = null;

        final BdvOptions options = Bdv.options();
        options.numRenderingThreads(numRenderingThreads);
        options.screenScales(screenScales);

        // check if raw data path is specified, and if it's not, try to get it from the attributes
        if (rawDataPath == null || rawDataPath.isEmpty()) {
            // try to read raw data location from the attributes of a prediction dataset
            final N5Reader n5 = N5Factory.createN5Reader(new N5Options(containerPath, new int[] {64}, null));
            final String[] datasets = n5.list("");
            if (datasets.length > 0) {
                final String predictionDataset = datasets[0];
                final String rawContainerPath = n5.getAttribute(predictionDataset, "raw_data_path", String.class);
                final String rawDatasetPath = n5.getAttribute(predictionDataset, "raw_ds", String.class);
                rawDataPath = Paths.get(rawContainerPath, rawDatasetPath).toString();
            }
        }

        // add raw data source
        if (rawDataPath != null && !rawDataPath.isEmpty()) {
            final Pair<String, String> rawDataN5AndGroup = pathToN5ContainerAndGroup(rawDataPath);
            if (rawDataN5AndGroup == null)
                throw new IllegalArgumentException("cannot extract N5 container and dataset path from raw data parameter");

            System.out.println("Add raw data: N5=" + rawDataN5AndGroup.getA() + ",  data=" + rawDataN5AndGroup.getB());

            final N5Reader n5 = N5Factory.createN5Reader(new N5Options(rawDataN5AndGroup.getA(), new int[] {64}, null));
            final String rawDataGroup = rawDataN5AndGroup.getB();
            final double[] resolution;
            {
                double[] resolutionArr = null;
                try {
                    resolutionArr = n5.getAttribute(rawDataGroup, "resolution", double[].class);
                } catch (final Throwable e) {
                    // try to read resolution attribute as voxel dimensions
                    final VoxelDimensions voxelDimensions = n5.getAttribute(rawDataGroup, "pixelResolution", FinalVoxelDimensions.class);
                    if (voxelDimensions != null) {
                        resolutionArr = new double[3];
                        voxelDimensions.dimensions(resolutionArr);
                    } else {
                        // use the same resolution as in the prediction datasets
                        final N5Reader n5Predictions = N5Factory.createN5Reader(new N5Options(containerPath, new int[] {64}, null));
                        final String[] predictionDatasets = n5Predictions.list("");
                        if (predictionDatasets.length > 0) {
                            final String predictionDataset = predictionDatasets[0];
                            resolutionArr = n5Predictions.getAttribute(predictionDataset, "resolution", double[].class);
                        }
                    }
                }
                resolution = resolutionArr;
            }

            @SuppressWarnings("rawtypes")
            final Pair<RandomAccessibleInterval<NativeType>[], double[][]> n5Sources;
            if (n5.datasetExists(rawDataN5AndGroup.getB())) {
                // this works for javac openjdk 8
                @SuppressWarnings({"rawtypes"})
                final RandomAccessibleInterval<NativeType> source = (RandomAccessibleInterval)N5Utils.openVolatile(n5, rawDataGroup);
                n5Sources = new ValuePair<>(new RandomAccessibleInterval[] {source}, new double[][]{{1, 1, 1}});
            } else {
                n5Sources = N5Utils.openMipmaps(n5, rawDataGroup, true);
            }

            /* make volatile */
            @SuppressWarnings("rawtypes")
            final RandomAccessibleInterval<NativeType>[] ras = n5Sources.getA();
            @SuppressWarnings("rawtypes")
            final RandomAccessibleInterval[] vras = new RandomAccessibleInterval[ras.length];
            Arrays.setAll(vras, k ->
                    VolatileViews.wrapAsVolatile(
                            n5Sources.getA()[k],
                            queue,
                            new CacheHints(LoadingStrategy.VOLATILE, 0, true)));

            final int[] con = {0, 255};
            final RandomAccessibleInterval<VolatileDoubleType>[] convertedSources = new RandomAccessibleInterval[n5Sources.getA().length];
            for (int k = 0; k < vras.length; ++k) {
                final Converter<AbstractVolatileNativeRealType<?, ?>, VolatileDoubleType> converter = (a, b) -> {
                        b.setValid(a.isValid());
                        if (b.isValid()) {
                            double v = a.get().getRealDouble();
                            v -= con[0];
                            v /= con[1] - con[0];
                            v *= 1000;
                            b.setReal(v);
                        }
                    };
                convertedSources[k] = Converters.convert(
                        (RandomAccessibleInterval<AbstractVolatileNativeRealType<?, ?>>)vras[k],
                        converter,
                        new VolatileDoubleType());
            }

            final AffineTransform3D sourceTransform = new AffineTransform3D();
            sourceTransform.scale(resolution[0], resolution[1], resolution[2]);

            final RandomAccessibleIntervalMipmapSource<VolatileDoubleType> mipmapSource = new RandomAccessibleIntervalMipmapSource<>(
                            convertedSources,
                            new VolatileDoubleType(),
                            n5Sources.getB(),
                            new FinalVoxelDimensions("nm", resolution),
                            sourceTransform,
                            "raw");

            bdv = BdvFunctions.show(
                    mipmapSource,
                    bdv == null ? options : options.addTo(bdv));
            bdv.setDisplayRange(0, 1000);
            bdv.setColor(new ARGBType(0xffffffff));
        }

        // add labels
        final N5Reader n5 = N5Factory.createN5Reader(new N5Options(containerPath, new int[] {64}, null));
        final String[] datasets = n5.list("");

        int id = 1;
        final List<Pair<String, Source<VolatileDoubleType>>> datasetsAndSources = new ArrayList<>();

        for (final String dataset : datasets) {
            System.out.println("Opening dataset /" + dataset);
            final double[] resolution = n5.getAttribute(dataset, "resolution", double[].class);
            final RandomAccessibleInterval<T> source = (RandomAccessibleInterval)N5Utils.openVolatile(n5, dataset);

            final RandomAccessibleInterval volatileSource = VolatileViews.wrapAsVolatile(
                            source,
                            queue,
                            new CacheHints(LoadingStrategy.VOLATILE, 0, true));

            final int idHash = hash(id);
            final Converter<AbstractVolatileNativeRealType<?, ?>, VolatileDoubleType> converter = (a, b) -> {
                b.setValid(a.isValid());
                if (b.isValid()) {
                    final int x = hash(Double.hashCode(a.get().getRealDouble()) ^ idHash);
                    final double v = ((double) x / Integer.MAX_VALUE + 1) * 500.0;
                    b.setReal(v);
                }
            };

            final RandomAccessibleInterval<VolatileDoubleType> convertedSource = Converters.convert(
                    volatileSource,
                    converter,
                    new VolatileDoubleType());

            final AffineTransform3D sourceTransform = new AffineTransform3D();
            sourceTransform.scale(resolution[0], resolution[1], resolution[2]);

            final RandomAccessibleIntervalMipmapSource<VolatileDoubleType> mipmapSource = new RandomAccessibleIntervalMipmapSource<>(
                    new RandomAccessibleInterval[] {convertedSource},
                    new VolatileDoubleType(),
                    new double[][] {{1, 1, 1}},
                    new FinalVoxelDimensions("nm", resolution),
                    sourceTransform,
                    dataset);

            bdv = BdvFunctions.show(
                    mipmapSource,
                    bdv == null ? options : options.addTo(bdv));
            bdv.setDisplayRange(0, 1000);
            bdv.setColor(new ARGBType(argb(id++)));

            datasetsAndSources.add(new ValuePair<>(dataset, mipmapSource));
        }

        // move all label sources into one group
        final SetupAssignments setupAssignments = bdv.getBdvHandle().getSetupAssignments();
        final List<ConverterSetup> converterSetups = setupAssignments.getConverterSetups();
        final List<MinMaxGroup> minMaxGroups = new ArrayList<>(setupAssignments.getMinMaxGroups());
        final int firstLabelSourceIndex = rawDataPath != null && !rawDataPath.isEmpty() ? 1 : 0;
        for (int i = firstLabelSourceIndex; i < minMaxGroups.size(); ++i)
            setupAssignments.moveSetupToGroup(converterSetups.get(i), minMaxGroups.get(minMaxGroups.size() - 1));

        // set display ranges
        minMaxGroups.get(minMaxGroups.size() - 1).setRange(0, 7000);
        minMaxGroups.get(0).setRange(0, 5000);

        bdv.setDisplayRange(0, 10000);

        // init extract labels dialog
        initExtractLabelsDialog(bdv.getBdvHandle(), datasetsAndSources, containerPath);

        return null;
    }

    private static <T extends NumericType<T> & NativeType<T>> void initExtractLabelsDialog(
            final BdvHandle bdvHandle,
            final List<Pair<String, Source<T>>> datasetsAndSources,
            final String inputContainer) {

        final TriggerBehaviourBindings bindings = bdvHandle.getTriggerbindings();
        final InputTriggerConfig config = new InputTriggerConfig();

        final ExtractLabelsDialog extractLabelsDialog = new ExtractLabelsDialog(
                bdvHandle.getViewerPanel(),
                datasetsAndSources,
                inputContainer,
                config,
                bdvHandle.getKeybindings());

        bindings.addBehaviourMap( "crop", extractLabelsDialog.getBehaviourMap() );
        bindings.addInputTriggerMap( "crop", extractLabelsDialog.getInputTriggerMap() );
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

    public static final void main(final String... args) {

        CommandLine.call(new ViewCosem(), args);
    }

    // hash code from https://stackoverflow.com/questions/664014/what-integer-hash-function-are-good-that-accepts-an-integer-hash-key
    private static final int hash(final int id) {
        int x = ((id >>> 16) ^ id) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return x;
    }

    private static final int datasetN(final N5Reader n5, final String group) throws IOException {

        if (n5.datasetExists(group))
            return n5.getAttribute(group, "dimensions", long[].class).length;
        else
            return n5.getAttribute(group + "/s0", "dimensions", long[].class).length;
    }

    private static Pair<String, String> pathToN5ContainerAndGroup(final String pathStr) {

        final Path path = Paths.get(pathStr).toAbsolutePath();
        if (Files.exists(path) && Files.isDirectory(path)) {
            for (int i = path.getNameCount(); i > 0; --i) {
                final Path subpath = path.subpath(0, i);
                try {
                    final String n5Path = "/" + subpath.toString();
                    final N5FSReader n5 = new N5FSReader(n5Path);
                    Version version;
                    try {
                        version = n5.getVersion();
                    } catch (final IOException f) {
                        f.printStackTrace(System.err);
                        continue;
                    }
                    if (version != null && version.getMajor() > 0) {
                        final String datasetPath = "/" + path.subpath(i, path.getNameCount()).toString();
                        if (n5.exists(datasetPath))
                            return new ValuePair<>(n5Path, datasetPath);
                    }
                } catch (final Exception e) {
                    e.printStackTrace(System.err);
                    return null;
                }
            }
        }
        return null;
    }
}
