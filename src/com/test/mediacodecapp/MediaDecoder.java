package com.test.mediacodecapp;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
public class MediaDecoder {
	private static final String TAG = "MediaDecoder";
	private static final int TIMEOUT_US = 10000;

	public interface OnFrameAvailabkeListener {
		void onFrameAvailable(long timestamp, int index, boolean EOS);
	}
	private MediaCodec mCodec;
	private MediaExtractor mExtractor;
	private final String mFilePath;
	private MediaFormat mFormat;
	private int mTrackIndex = -1;
	private final Surface mSurface;
	private final OnFrameAvailabkeListener mFrameListener;
	public MediaDecoder(String path, Surface surface, MediaCodecApp activity, OnFrameAvailabkeListener l) {
		mFilePath = path;
		mSurface = surface;
		mFrameListener = l;
		try {
			mExtractor = new MediaExtractor();
			mExtractor.setDataSource(mFilePath);
			for (int i = 0; i < mExtractor.getTrackCount(); i++) {
				MediaFormat format = mExtractor.getTrackFormat(i);
				String mime = format.getString(MediaFormat.KEY_MIME);
				if (mime != null && mime.toLowerCase(Locale.US).startsWith("video/")) {
					mFormat = format;
					mTrackIndex = i;
					break;
				}
			}

			if (mTrackIndex == -1) {
				throw new IOException("No video track found in " + mFilePath);
			}

		} catch (IOException e) {
			Log.e(TAG, "Failed to initialize MediaDecoder", e);
		}
	}

	public void decode() {
		if (mFormat == null) {
			Log.e(TAG, "Cannot decode without a valid media format.");
			return;
		}
		try {
			createDecoder();
		} catch (IOException e) {
			Log.e(TAG, "Failed to create decoder", e);
			return;
		}
		new Thread(this::doDecode).start();
	}

	private void createDecoder() throws IOException {
		String mime = mFormat.getString(MediaFormat.KEY_MIME);
		if (mime == null) {
			throw new IOException("MIME type is null, cannot create decoder.");
		}
		mCodec = MediaCodec.createDecoderByType(mime);
		mCodec.configure(mFormat, mSurface, null, 0);
		mCodec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
		mCodec.start();
		mExtractor.selectTrack(mTrackIndex);
	}

	private void doDecode() {
		boolean sawInputEOS = false;
		boolean sawOutputEOS = false;
		final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
		while (!sawOutputEOS) {
			if (!sawInputEOS) {
				int inputBufIndex = mCodec.dequeueInputBuffer(TIMEOUT_US);
				if (inputBufIndex >= 0) {
					ByteBuffer inputBuf = mCodec.getInputBuffer(inputBufIndex);
					int sampleSize = mExtractor.readSampleData(inputBuf, 0);
					if (sampleSize < 0) {
						Log.d(TAG, "Input EOS reached.");
						sawInputEOS = true;
						sampleSize = 0;
					}
					long presentationTimeUs = mExtractor.getSampleTime();
					mCodec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs,sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    if (!sawInputEOS) {
						mExtractor.advance();
					}
				}
			}
			int outputBufIndex = mCodec.dequeueOutputBuffer(info, TIMEOUT_US);
			if (outputBufIndex >= 0) {
				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					Log.d(TAG, "Output EOS reached.");
					sawOutputEOS = true;
				}
				boolean render = info.size > 0;
				if (render && mFrameListener != null) {
					mFrameListener.onFrameAvailable(info.presentationTimeUs, outputBufIndex, sawOutputEOS);
				} else {
					mCodec.releaseOutputBuffer(outputBufIndex, false);
				}

			} else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				mFormat = mCodec.getOutputFormat();
				Log.d(TAG, "Output format has changed to " + mFormat);
				getColorFormat(mFormat);
			} else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
				//Log.d(TAG, "dequeueOutputBuffer timed out!");
			}
		}
		release();
	}
	public void render(int index) {
		// The boolean parameter indicates whether to render the buffer to the surface
		mCodec.releaseOutputBuffer(index, true);
	}
	public void release() {
		if (mCodec != null) {
			try {
				mCodec.stop();
				mCodec.release();
			} catch (Exception e) {
				Log.e(TAG, "Error releasing MediaCodec", e);
			}
			mCodec = null;
		}
		if (mExtractor != null) {
			mExtractor.release();
			mExtractor = null;
		}
	}
	private void getColorFormat(MediaFormat format) {
		if (!format.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
			return;
		}
		int colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
		int QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03;
		String formatString = "";
		if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format12bitRGB444) {
			formatString = "COLOR_Format12bitRGB444";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format16bitARGB1555) {
			formatString = "COLOR_Format16bitARGB1555";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format16bitARGB4444) {
			formatString = "COLOR_Format16bitARGB4444";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format16bitBGR565) {
			formatString = "COLOR_Format16bitBGR565";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format16bitRGB565) {
			formatString = "COLOR_Format16bitRGB565";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format18bitARGB1665) {
			formatString = "COLOR_Format18bitARGB1665";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format18BitBGR666) {
			formatString = "COLOR_Format18BitBGR666";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format18bitRGB666) {
			formatString = "COLOR_Format18bitRGB666";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format19bitARGB1666) {
			formatString = "COLOR_Format19bitARGB1666";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format24BitABGR6666) {
			formatString = "COLOR_Format24BitABGR6666";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format24bitARGB1887) {
			formatString = "COLOR_Format24bitARGB1887";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format24BitARGB6666) {
			formatString = "COLOR_Format24BitARGB6666";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format24bitBGR888) {
			formatString = "COLOR_Format24bitBGR888";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format24bitRGB888) {
			formatString = "COLOR_Format24bitRGB888";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format25bitARGB1888) {
			formatString = "COLOR_Format25bitARGB1888";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888) {
			formatString = "COLOR_Format32bitARGB8888";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format32bitBGRA8888) {
			formatString = "COLOR_Format32bitBGRA8888";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_Format8bitRGB332) {
			formatString = "COLOR_Format8bitRGB332";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatCbYCrY) {
			formatString = "COLOR_FormatCbYCrY";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatCrYCbY) {
			formatString = "COLOR_FormatCrYCbY";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatL16) {
			formatString = "COLOR_FormatL16";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatL2) {
			formatString = "COLOR_FormatL2";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatL24) {
			formatString = "COLOR_FormatL24";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatL32) {
			formatString = "COLOR_FormatL32";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatL4) {
			formatString = "COLOR_FormatL4";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatL8) {
			formatString = "COLOR_FormatL8";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatMonochrome) {
			formatString = "COLOR_FormatMonochrome";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer10bit) {
			formatString = "COLOR_FormatRawBayer10bit";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer8bit) {
			formatString = "COLOR_FormatRawBayer8bit";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer8bitcompressed) {
			formatString = "COLOR_FormatRawBayer8bitcompressed";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYCbYCr) {
			formatString = "COLOR_FormatYCbYCr";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYCrYCb) {
			formatString = "COLOR_FormatYCrYCb";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV411PackedPlanar) {
			formatString = "COLOR_FormatYUV411PackedPlanar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV411Planar) {
			formatString = "COLOR_FormatYUV411Planar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar) {
			formatString = "COLOR_FormatYUV420PackedPlanar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar) {
			formatString = "COLOR_FormatYUV420PackedSemiPlanar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedPlanar) {
			formatString = "COLOR_FormatYUV422PackedPlanar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedSemiPlanar) {
			formatString = "COLOR_FormatYUV422PackedSemiPlanar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Planar) {
			formatString = "COLOR_FormatYUV422Planar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedSemiPlanar) {
			formatString = "COLOR_FormatYUV422PackedSemiPlanar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Planar) {
			formatString = "COLOR_FormatYUV422Planar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422SemiPlanar) {
			formatString = "COLOR_FormatYUV422SemiPlanar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV444Interleaved) {
			formatString = "COLOR_FormatYUV444Interleaved";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar) {
			formatString = "COLOR_QCOM_FormatYUV420SemiPlanar";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar) {
			formatString = "COLOR_TI_FormatYUV420PackedSemiPlanar";
		} else if (colorFormat == QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka) {
			formatString = "QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka";
		} else if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
			formatString = "COLOR_FormatYUV420Planar";
		}
		Log.i("TAG", "Detected color format: " + colorFormat);
	}
}