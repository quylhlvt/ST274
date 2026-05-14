package com.oc.catemoji.catoc.core.custom.listener.listenerdraw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import com.oc.catemoji.catoc.core.custom.DrawView
import java.io.IOException

class VideoEncoder(
    private val context: Context,
    private val listDrawView: MutableList<DrawView>,
    private val outputFilePath: String,
    private val frameRate: Int,
    private val callback: (Boolean, String?) -> Unit
) {
    private val width = listDrawView[0].width
    private val height = listDrawView[0].height
    private val bitRate = 6000000

    fun startEncoding() {
        // Phương thức sẽ lặp lại cho đến khi thành công
        var success = false
        while (!success) {
            try {
                performEncoding()
                success = true // Nếu không xảy ra lỗi, đặt success thành true để thoát vòng lặp
            } catch (e: Exception) {
                Log.d("createVideo", "Retrying due to error: ${e.message}")
                // Đợi một khoảng thời gian trước khi thử lại (Tùy chọn)
                Thread.sleep(1000)
            }
        }
    }

    private fun performEncoding() {
        var mediaMuxer: MediaMuxer? = null
        var mediaCodec: MediaCodec? = null
        var inputSurface: Surface? = null

        try {
            val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
            }

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }

            mediaMuxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            var videoTrackIndex = -1

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            listDrawView.forEachIndexed { index, drawView ->
                drawView.draw(canvas)

                val surfaceCanvas = inputSurface!!.lockCanvas(null)
                surfaceCanvas.drawBitmap(bitmap, 0f, 0f, null)
                inputSurface!!.unlockCanvasAndPost(surfaceCanvas)

                var outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputBufferIndex >= 0) {
                    val encodedData = mediaCodec.getOutputBuffer(outputBufferIndex) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)

                        if (videoTrackIndex == -1) {
                            val newFormat = mediaCodec.outputFormat
                            videoTrackIndex = mediaMuxer.addTrack(newFormat)
                            mediaMuxer.start() // Start the muxer after adding track
                        }

                        mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
                }
            }

            // Signal end of stream
            mediaCodec.signalEndOfInputStream()

            // Drain any remaining output buffers
            var outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
            while (outputBufferIndex >= 0) {
                val encodedData = mediaCodec.getOutputBuffer(outputBufferIndex) ?: continue
                if (bufferInfo.size != 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                }
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
            }

            mediaCodec.stop()
            mediaCodec.release()
            mediaMuxer.stop()
            mediaMuxer.release()

            callback(true, null)
        } catch (e: IOException) {
            throw e // Đẩy lỗi ra ngoài để vòng lặp có thể bắt lại và thử lại
        } catch (e: Exception) {
            throw e // Đẩy lỗi ra ngoài để vòng lặp có thể bắt lại và thử lại
        } finally {
            mediaCodec?.release()
            mediaMuxer?.release()
        }
    }
}


