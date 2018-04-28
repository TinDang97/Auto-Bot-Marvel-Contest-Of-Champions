package com.trtin.autobot;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    Button btnStart;
    Button btnStop;
    private static final int REQUEST_SCREENSHOT = 59706;
    private Intent mIntent;
//    Spinner mode;
//    EditText smallE;
//    EditText superE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mIntent = new Intent(this, AutoBot.class);
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            requestPermissions(new String[]{"android.permission.WAKE_LOCK"}, 123);
//        }

        btnStart = (Button) findViewById(R.id.btnStart);
        btnStop = (Button) findViewById(R.id.btnStop);
//        mode = (Spinner) findViewById(R.id.mode);
//        smallE = (EditText) findViewById(R.id.numSmall);
//        superE = (EditText) findViewById(R.id.numSuper);
//
//        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
//                R.array.mode, android.R.layout.simple_spinner_item);
//        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//
//        mode.setAdapter(adapter);
//        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                switch (position){
//                    case 1:
//                    case 2:
//                    case 5:
//                    case 6:
//                        smallE.setEnabled(true);
//                        superE.setEnabled(true);
//                        break;
//
//                    case 3:
//                        smallE.setEnabled(true);
//                        superE.setEnabled(false);
//                        break;
//                    case 4:
//                        smallE.setEnabled(false);
//                        superE.setEnabled(true);
//                        break;
//                    default:
//                        superE.setEnabled(false);
//                        smallE.setEnabled(false);
//                }
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {
//
//            }
//        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnStart.setEnabled(false);
                btnStop.setEnabled(true);
                mIntent.setAction(AutoBot.ACTION_START);
//                mIntent.putExtra("smallE", Integer.parseInt(smallE.getText().toString()));
//                mIntent.putExtra("superE", Integer.parseInt(superE.getText().toString()));
//                switch (mode.getSelectedItemPosition()){
//                    case 1:
//                    case 2:
//                        mIntent.putExtra("mode", "swap");
//                        mIntent.putExtra("executeModeE", mode.getSelectedItemPosition()-1);
//                        break;
//                    case 3:
//                        mIntent.putExtra("mode", "small");
//                        break;
//                    case 4:
//                        mIntent.putExtra("mode", "super");
//                        break;
//                    case 5:
//                        mIntent.putExtra("mode", "smallF");
//                        break;
//                    case 6:
//                        mIntent.putExtra("mode", "superF");
//                        break;
//                }
                startService(mIntent);
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
                stopService(mIntent);
            }
        });

        if(AutoBot.isStarted){
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
        }
        else {
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
        }

        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mgr.createScreenCaptureIntent(),
                REQUEST_SCREENSHOT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==REQUEST_SCREENSHOT) {
            if (resultCode==RESULT_OK) {
                mIntent.putExtra(AutoBot.EXTRA_RESULT_CODE, resultCode)
                                .putExtra(AutoBot.EXTRA_RESULT_INTENT, data);
            }
            else
                btnStart.setEnabled(false);
        }
    }


}
