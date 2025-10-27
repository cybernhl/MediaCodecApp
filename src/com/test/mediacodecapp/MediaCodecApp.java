package com.test.mediacodecapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MediaCodecApp extends Activity {
	private static final String TAG = "MediaCodecApp";
	private static final String VIDEO_ASSET_NAME = "frameCount.mp4"; // 假設您的影片在 assets 中是這個名字
	private String mVideoPath; // 影片將被複製到此路徑
	private static final int PERMISSION_REQUEST_CODE = 1001;
	private SurfaceView mSurfaceView = null;
	private Surface mSurface = null;
	private MediaDecoder mDecoder = null;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.activity_main);
		mVideoPath = new File(getFilesDir(), VIDEO_ASSET_NAME).getAbsolutePath();
		mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
		mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
				mSurface = null;
			}

			@Override
			public void surfaceCreated(@NonNull SurfaceHolder holder) {
				// Not used
			}

			@Override
			public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
				mSurface = holder.getSurface();
			}
		});

		Button extractButton = (Button) findViewById(R.id.button_snapshot);
		extractButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				checkPermissionAndDecode();
			}
		});
	}

	private void checkPermissionAndDecode() {
		String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ?
				Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;

		if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
		} else {
			decodeFrames(mSurface);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PERMISSION_REQUEST_CODE) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				decodeFrames(mSurface);
			} else {
				Log.w(TAG, "Storage permission was denied. The app will try to proceed using internal storage.");
				Toast.makeText(this, "儲存權限已被拒絕。應用將嘗試使用內部檔案。", Toast.LENGTH_LONG).show();
				decodeFrames(mSurface);
			}
		}
	}
	private void decodeFrames(Surface surface) {
		File videoFile = new File(mVideoPath);
		if (!videoFile.exists()) {
			copyAssets();
		}
		if (mDecoder != null) {
			mDecoder.release();
		}
		mDecoder = new MediaDecoder(mVideoPath, surface, this, new MediaDecoder.OnFrameAvailabkeListener() {
			@Override
			public void onFrameAvailable(long timestamp, int index, boolean EOS) {
				Log.i(TAG, "Frame available, index=" + index + ", EOS=" + EOS);
				mDecoder.render(index);
				if (EOS) {
					mDecoder.release();
					mDecoder = null;
					MediaCodecApp.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(MediaCodecApp.this, "End of stream", Toast.LENGTH_LONG).show();
						}
					});
				}
			}
		});
		mDecoder.decode();
	}
	private void copyAssets() {
		AssetManager assetManager = getAssets();
		try (InputStream in = assetManager.open(VIDEO_ASSET_NAME);
			 OutputStream out = new FileOutputStream(mVideoPath)) {
			copyFile(in, out);
		} catch (IOException e) {
			Log.e(TAG, "Failed to copy asset file: " + VIDEO_ASSET_NAME, e);
			runOnUiThread(()-> Toast.makeText(this, "複製影片檔案失敗: " + e.getMessage(), Toast.LENGTH_LONG).show());
		}
	}
	private void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (mDecoder != null) {
			mDecoder.release();
			mDecoder = null;
		}
	}
}