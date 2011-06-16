package de.joergjahnke.gameboy.android;


import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import java.util.HashMap;
import java.util.Map;

import de.joergjahnke.common.android.OrientationSensorNotifier;


/**
 * Dialog to edit the application settings
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class EditSettingsDialog extends Activity {
	/**
	 * Maps the joystick port numbers to joystick port IDs from the resource file and vice versa
	 */
	private final static Map<Integer,Integer> frameSkipIdMap = new HashMap<Integer,Integer>();
	
	static {
		frameSkipIdMap.put( 1, R.id.fs1 );
		frameSkipIdMap.put( 2, R.id.fs2 );
		frameSkipIdMap.put( 3, R.id.fs3 );
		frameSkipIdMap.put( 4, R.id.fs4 );
		frameSkipIdMap.put( 5, R.id.fs5 );
		frameSkipIdMap.put( R.id.fs1, 1 );
		frameSkipIdMap.put( R.id.fs2, 2 );
		frameSkipIdMap.put( R.id.fs3, 3 );
		frameSkipIdMap.put( R.id.fs4, 4 );
		frameSkipIdMap.put( R.id.fs5, 5 );
	}
	
	
    @Override
    public void onCreate( final Bundle icicle ) {
        super.onCreate( icicle );
        setContentView( R.layout.editsettingsdialog );
        
        // set default selections
    	final RadioGroup frameSkipRadio = (RadioGroup)findViewById( R.id.frameskip );
    	final CheckBox antialiasingCheckBox = (CheckBox)findViewById( R.id.antialiasingActive );
    	final CheckBox soundCheckBox = (CheckBox)findViewById( R.id.soundActive );
    	final CheckBox orientationSensorCheckBox = (CheckBox)findViewById( R.id.orientationSensorActive );
    	
    	frameSkipRadio.check( frameSkipIdMap.get( getIntent().getIntExtra( "de.joergjahnke.gameboy.android.frameSkip", 3 ) ) );
    	antialiasingCheckBox.setChecked( getIntent().getBooleanExtra( "de.joergjahnke.gameboy.android.antialiasing", true ) );
    	soundCheckBox.setChecked( getIntent().getBooleanExtra( "de.joergjahnke.gameboy.android.soundActive", false ) );
    	orientationSensorCheckBox.setChecked( getIntent().getBooleanExtra( "de.joergjahnke.gameboy.android.orientationSensorActive", false ) );
    	
    	if(!OrientationSensorNotifier.isSupported()) {
    		findViewById( R.id.orientationSensorActiveText ).setVisibility(View.GONE);
    		orientationSensorCheckBox.setVisibility(View.GONE);
    	}

        // install button listener which will take care of returning the results
        final Button okButton = (Button)findViewById( R.id.ok );
        
        okButton.setOnClickListener(
        	new OnClickListener() {
        		public void onClick( View arg0 ) {
        			final Bundle extras = new Bundle();
        	    	final int frameSkipId = frameSkipRadio.getCheckedRadioButtonId();
        	    	
        	    	extras.putInt( "de.joergjahnke.gameboy.android.frameSkip", frameSkipIdMap.get( frameSkipId ) );
        	    	
        	    	final boolean useAntialiasing = antialiasingCheckBox.isChecked();
        	    	
        	    	extras.putBoolean( "de.joergjahnke.gameboy.android.antialiasing", useAntialiasing );
        	    	
        	    	final boolean soundActive = soundCheckBox.isChecked();
        	    	
        	    	extras.putBoolean( "de.joergjahnke.gameboy.android.soundActive", soundActive );
        	    	
        	    	final boolean orientationSensorActive = orientationSensorCheckBox.isChecked();
        	    	
        	    	extras.putBoolean( "de.joergjahnke.gameboy.android.orientationSensorActive", orientationSensorActive );
        	    	
        			setResult( RESULT_OK, null, extras );
        			finish();
        		}
        	}
        );
    }    	
}
