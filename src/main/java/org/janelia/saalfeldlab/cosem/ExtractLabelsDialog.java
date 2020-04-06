package org.janelia.saalfeldlab.cosem;

import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import ij.gui.GenericDialog;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.InputActionBindings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.List;
import java.util.*;

public class ExtractLabelsDialog< T extends NumericType< T > & NativeType< T > >
{
    final protected ViewerPanel viewer;

    private final RealPoint lastClick = new RealPoint( 3 );
    private final List<Pair<String, Source<T>>> datasetsAndSources;
    private final String inputContainer;
    private String outputPath = "";

    static private int width = 1024;
    static private int height = 1024;
    static private int depth = 512;
    static private int scaling = 1;
    static private int threshold = 128;
    static private int blockSize = 128;

    // for behavioUrs
    private final BehaviourMap behaviourMap = new BehaviourMap();
    private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
    private final InputTriggerAdder inputAdder;

    // for keystroke actions
    private final ActionMap ksActionMap = new ActionMap();
    private final InputMap ksInputMap = new InputMap();

    public ExtractLabelsDialog(
            final ViewerPanel viewer,
            final List<Pair<String, Source<T>>> datasetsAndSources,
            final String inputContainer,
            final InputTriggerConfig config,
            final InputActionBindings inputActionBindings )
    {
        this.viewer = viewer;
        this.datasetsAndSources = datasetsAndSources;
        this.inputContainer = inputContainer;

        inputAdder = config.inputTriggerAdder( inputTriggerMap, "crop" );

        new Crop( "crop", "SPACE" ).register();

        inputActionBindings.addActionMap( "select", ksActionMap );
        inputActionBindings.addInputMap( "select", ksInputMap );
    }

    ////////////////
    // behavioUrs //
    ////////////////

    public BehaviourMap getBehaviourMap()
    {
        return behaviourMap;
    }

    public InputTriggerMap getInputTriggerMap()
    {
        return inputTriggerMap;
    }

    private abstract class SelfRegisteringBehaviour implements Behaviour
    {
        private final String name;
        private final String[] defaultTriggers;

        public SelfRegisteringBehaviour( final String name, final String... defaultTriggers )
        {
            this.name = name;
            this.defaultTriggers = defaultTriggers;
        }

        public void register()
        {
            behaviourMap.put( name, this );
            inputAdder.put( name, defaultTriggers );
        }
    }

    private class Crop extends SelfRegisteringBehaviour implements ClickBehaviour
    {
        private	List< TextField > centerPointTextFields;
        private long[] centerPoint;

        public Crop( final String name, final String ... defaultTriggers )
        {
            super( name, defaultTriggers );
        }

        @Override
        public void click( final int x, final int y )
        {
            viewer.displayToGlobalCoordinates(x, y, lastClick);

            centerPoint = new long[ lastClick.numDimensions() ];
            for ( int i = 0; i < centerPoint.length; ++i )
                centerPoint[ i ] = Math.round( lastClick.getDoublePosition( i ) );

            final GenericDialog gd = new GenericDialog( "Extract labels" );

            gd.addCheckbox( "Custom_center_point", false );
            gd.addNumericField( "X : ", centerPoint[ 0 ], 0 );
            gd.addNumericField( "Y : ", centerPoint[ 1 ], 0 );
            gd.addNumericField( "Z : ", centerPoint[ 2 ], 0 );
            gd.addPanel( new Panel() );
            gd.addNumericField( "width : ", width, 0, 5, "px" );
            gd.addNumericField( "height : ", height, 0, 5, "px" );
            gd.addNumericField( "depth : ", depth, 0, 5, "px" );
            gd.addNumericField( "scaling : ", scaling, 0, 5, "" );
            gd.addNumericField( "threshold : ", threshold, 0, 5, "" );
            gd.addNumericField( "block size: ", blockSize, 0, 5, "" );

            gd.addPanel( new Panel() );
            final String[] datasetLabels = new String[datasetsAndSources.size()];
            Arrays.setAll(datasetLabels, i -> datasetsAndSources.get(i).getA());
            int numColumns = 3;
            int numRows = (datasetLabels.length / numColumns) + (datasetLabels.length % numColumns == 0 ? 0 : 1);
            gd.addCheckboxGroup(numRows, numColumns, datasetLabels, new boolean[datasetLabels.length]);


            final Panel selectBtnPanel = new Panel();
            final Button selectAllBtn = new Button("Select All"), selectNoneBtn = new Button("Select none");
            selectBtnPanel.add(selectAllBtn);
            selectBtnPanel.add(selectNoneBtn);
            gd.add(selectBtnPanel);
            gd.addPanel(new Panel());

            final Button browseBtn = new Button("Browse");
            gd.add(browseBtn);
            gd.addPanel(new Panel());

            centerPointTextFields = new ArrayList<>();
            for ( int i = 0; i < 3; ++i )
                centerPointTextFields.add( ( TextField ) gd.getNumericFields().get( i ) );

            final Checkbox centerPointCheckbox = ( Checkbox ) gd.getCheckboxes().get( 0 );
            centerPointCheckbox.addItemListener(
                    e -> setCenterPointTextFieldsEnabled( e.getStateChange() == ItemEvent.SELECTED ) );

            gd.addComponentListener( new ComponentAdapter()
                                     {
                                         @Override
                                         public void componentShown( final ComponentEvent e )
                                         {
                                             setCenterPointTextFieldsEnabled( false );
                                         }
                                     } );


            // add 'select all' and 'select none' listeners
            final List<Checkbox> datasetCheckboxes = new ArrayList<>();
            for (int i = 0; i < datasetLabels.length; ++i)
                datasetCheckboxes.add( (Checkbox) gd.getCheckboxes().get( i + 1 ) );
            selectAllBtn.addActionListener(
                    e -> {
                        for ( final Checkbox c : datasetCheckboxes)
                            c.setState(true);
                    } );

            selectNoneBtn.addActionListener(
                    e -> {
                        for ( final Checkbox c : datasetCheckboxes)
                            c.setState(false);
                    } );


            // add browse handler
            browseBtn.addActionListener(
                    e -> {
                        File directory = !outputPath.isEmpty() ? new File( outputPath ) : null;
                        final JFileChooser directoryChooser = new JFileChooser( directory );
                        directoryChooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
                        if ( directoryChooser.showOpenDialog( gd ) ==  JFileChooser.APPROVE_OPTION ) {
                            outputPath = directoryChooser.getSelectedFile().getAbsolutePath();
                        }
                    } );

            gd.showDialog();

            if ( gd.wasCanceled() )
                return;

            if ( outputPath.isEmpty() ) {
                final GenericDialog message = new GenericDialog("");
                message.addMessage("Please select output path.");
                message.hideCancelButton();
                message.showDialog();
                return;
            }

            final boolean customCenterPoint = gd.getNextBoolean();
            for ( int i = 0; i < 3; ++i )
                centerPoint[ i ] = ( long ) gd.getNextNumber();

            width = ( int )gd.getNextNumber();
            height = ( int )gd.getNextNumber();
            depth = ( int )gd.getNextNumber();
            scaling = (int)gd.getNextNumber();
            threshold = (int)gd.getNextNumber();
            blockSize = (int)gd.getNextNumber();

            if ( customCenterPoint )
                lastClick.setPosition( centerPoint );

            final long[] size = new long[] { width, height, depth };
            final long[] worldMin = new long[3], worldMax = new long[3];
            Arrays.setAll(worldMin, d -> Math.round( centerPoint[d] - 0.5 * size[d] ) );
            Arrays.setAll(worldMax, d -> worldMin[d] + size[d] - 1);

            final AffineTransform3D transform = new AffineTransform3D();
            final int scaleLevel = 0;
            final int timepoint = 1;
            datasetsAndSources.get( 0 ).getB().getSourceTransform( timepoint, scaleLevel, transform );

            final double[] sourceMin = new double[3], sourceMax = new double[3];
            Arrays.setAll(sourceMin, d -> worldMin[d]);
            Arrays.setAll(sourceMax, d -> worldMax[d]);
            transform.applyInverse(sourceMin, sourceMin);
            transform.applyInverse(sourceMax, sourceMax);
            final Interval cropInterval = Intervals.smallestContainingInterval(new FinalRealInterval(sourceMin, sourceMax));

            final int[] blockSizeArr = new int[3];
            Arrays.fill(blockSizeArr, blockSize);

            final List<String> datasetsToExtract = new ArrayList<>();
            for (int i = 0; i < datasetLabels.length; ++i)
                if ( datasetCheckboxes.get(i).getState())
                    datasetsToExtract.add(datasetLabels[i]);

            try {
                System.out.println("Extracting the following classes in world interval [min=" + Arrays.toString(worldMin) + ", max=" + Arrays.toString(worldMax) + "] to " + outputPath + ":");
                for (final String d : datasetsToExtract)
                    System.out.println("  " + d);

                ExtractLabels.extractLabels(
                        inputContainer,
                        cropInterval,
                        outputPath,
                        OptionalDouble.of(scaling),
                        OptionalDouble.of(threshold),
                        Optional.of(datasetsToExtract),
                        Optional.of(blockSizeArr));

                System.out.println("Done!");
            } catch (final Exception e) {
                e.printStackTrace();
            }

            viewer.requestRepaint();
        }

        private void setCenterPointTextFieldsEnabled( final boolean enabled )
        {
            for ( int i = 0; i < centerPointTextFields.size(); ++i )
            {
                final TextField tf = centerPointTextFields.get( i );
                tf.setEnabled( enabled );

                if ( !enabled )
                    tf.setText( Long.toString( centerPoint[ i ] ) );
            }
        }
    }
}