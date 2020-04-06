package org.janelia.saalfeldlab.cosem;

import net.imglib2.*;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Igor Pisarev
 */
public class ExtractLabels implements Callable<Void> {

    private static int defaultClassId = 100;

    public static <T extends NativeType<T> & RealType<T>> void extractLabels(
            final String containerPath,
            final Interval cropInterval,
            final String outputPath,
            final OptionalDouble scalingOptional,
            final OptionalDouble thresholdOptional,
            final Optional<List<String>> datasetsOptional,
            final Optional<int[]> blockSizeOptional) throws IOException, ExecutionException, InterruptedException {

        final N5Reader n5Reader = new N5FSReader(containerPath);
        final List<String> datasets = datasetsOptional.isPresent() ? datasetsOptional.get() :
                Arrays.asList(n5Reader.list("/"));

        final double scaling = scalingOptional.orElse(1);
        final double threshold = thresholdOptional.orElse(0);

        final AffineTransform3D upscaleTransform = new AffineTransform3D();
        upscaleTransform
                .preConcatenate(new Scale3D(scaling, scaling, scaling));
//                .preConcatenate(new Translation3D(-0.5, -0.5, -0.5));

        final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        final CosemClassToId cosemClassToId = new CosemClassToId();

        for (final String dataset : datasets) {

            final int classId;
            {
                final int cosemClassId = cosemClassToId.getClassId(dataset);
                if (cosemClassId != -1) {
                    classId = cosemClassId;
                } else {
                    System.err.println("Cannot find ID for class " + cosemClassToId.getClassKey(dataset) + ", using default id=" + defaultClassId + " instead");
                    classId = defaultClassId;
                }
            }

            final RandomAccessibleInterval<T> img = N5Utils.open(n5Reader, dataset);
            final RealRandomAccessible<T> interpolatedImg = Views.interpolate(Views.extendBorder(img), new NLinearInterpolatorFactory<>());
            final RandomAccessible<T> upscaledImg = RealViews.affine(interpolatedImg, upscaleTransform);

            final double[] upscaledCropMin = new double[3], upscaledCropMax = new double[3];
            Arrays.setAll(upscaledCropMin, d -> cropInterval.realMin(d));
            Arrays.setAll(upscaledCropMax, d -> cropInterval.realMax(d) + 1);
            upscaleTransform.apply(upscaledCropMin, upscaledCropMin);
            upscaleTransform.apply(upscaledCropMax, upscaledCropMax);
            Arrays.setAll(upscaledCropMax, d -> upscaledCropMax[d] - 1);
            final Interval upscaledCropInterval = Intervals.smallestContainingInterval(new FinalRealInterval(upscaledCropMin, upscaledCropMax));
            final RandomAccessibleInterval<T> upscaledCrop = Views.interval(upscaledImg, upscaledCropInterval);
            // save as uint64 so that paintera recognizes it as label data
            final RandomAccessibleInterval<UnsignedLongType> upscaledCropMask = Converters.convert(upscaledCrop, (in, out) -> out.set(in.getRealDouble() >= threshold ? classId : 0), new UnsignedLongType());

            final int[] outBlockSize = blockSizeOptional.orElse(n5Reader.getDatasetAttributes(dataset).getBlockSize());
            final String outputDatasetPath = Paths.get("volumes/labels", dataset).toString();
            final N5Writer n5Writer = new N5FSWriter(outputPath);

            N5Utils.save(
                    upscaledCropMask,
                    n5Writer,
                    outputDatasetPath,
                    outBlockSize,
                    new GzipCompression(),
                    threadPool);

            n5Writer.setAttribute(outputDatasetPath, "maxId", classId);

            final double[] inputResolution = n5Reader.getAttribute(dataset, "resolution", double[].class);
            final double[] outputResolution = new double[3];
            Arrays.setAll(outputResolution, d -> (inputResolution != null ? inputResolution[d] : 1) * scaling);
            n5Writer.setAttribute(outputDatasetPath, "resolution", outputResolution);

            final double[] inputOffset = n5Reader.getAttribute(dataset, "offset", double[].class);
            final double[] scaledCropMin = new double[3];
            Arrays.setAll(scaledCropMin, d -> cropInterval.realMin(d) * outputResolution[d]);
            final double[] inputScaledOffset = new double[3];
            Arrays.setAll(inputScaledOffset, d -> (inputOffset != null ? inputOffset[d] : 0) * scaling);
            final double[] outputOffset = new double[3];
            Arrays.setAll(outputOffset, d -> inputScaledOffset[d] + scaledCropMin[d]);
            n5Writer.setAttribute(outputDatasetPath, "offset", outputOffset);
        }

        threadPool.shutdown();
    }

    @CommandLine.Option(names = {"-i", "--container"}, required = true, description = "container path, e.g. -i $HOME/fib19.n5")
    private String containerPath = null;

    @CommandLine.Option(names = {"-d", "--datasets"}, required = false, description = "datasets (optional, by default all will be included)")
    private List<String> datasets = null;

    @CommandLine.Option(names = {"-o", "--output"}, required = true, description = "output container")
    private String outputPath = null;

    @CommandLine.Option(names = {"-min", "--min"}, required = true, description = "crop min")
    private String cropMinStr = null;

    @CommandLine.Option(names = {"-max", "--max"}, required = true, description = "crop max")
    private String cropMaxStr = null;

    @CommandLine.Option(names = {"-b", "--blockSize"}, required = false, description = "block size")
    private String blockSizeStr = null;

    @CommandLine.Option(names = {"-t", "--threshold"}, required = false, description = "threshold")
    private double threshold = 128;

    @CommandLine.Option(names = {"-s", "--scale"}, required = false, description = "scaling")
    private double scaling = 2;

    protected static final long[] parseCSLongArray(final String csv) {

        if (csv == null)
            return null;
        final String[] stringValues = csv.split(",\\s*");
        final long[] array = new long[stringValues.length];
        try {
            for (int i = 0; i < array.length; ++i)
                array[i] = Long.parseLong(stringValues[i]);
        } catch (final NumberFormatException e) {
            e.printStackTrace(System.err);
            return null;
        }
        return array;
    }

    protected static final int[] parseCSIntArray(final String csv) {

        if (csv == null)
            return null;
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

    @Override
    public Void call() throws IOException, ExecutionException, InterruptedException {

        final Interval cropInterval = new FinalInterval(
                parseCSLongArray(cropMinStr),
                parseCSLongArray(cropMaxStr));

        extractLabels(
                containerPath,
                cropInterval,
                outputPath,
                OptionalDouble.of(scaling),
                OptionalDouble.of(threshold),
                Optional.ofNullable(datasets),
                Optional.ofNullable(parseCSIntArray(blockSizeStr))
        );

        return null;
    }

    @SuppressWarnings( "unchecked" )
    public static final void main(final String... args) {

        CommandLine.call(new ExtractLabels(), args);
    }
}
