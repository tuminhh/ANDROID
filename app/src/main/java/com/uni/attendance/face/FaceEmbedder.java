package com.uni.attendance.face;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Trích xuất vector đặc trưng khuôn mặt bằng mô hình MobileFaceNet (TFLite).
 * Mô hình đi kèm có đầu vào [2,112,112,3] và đầu ra [2,192]: nó luôn xử lý một lô 2 ảnh
 * (mã gốc dùng để so hai mặt cùng lúc). Vì vậy ở đây ta nhân đôi ảnh vào đủ lô rồi chỉ lấy hàng đầu.
 * Kích thước lô, cỡ ảnh và số chiều vector được đọc tự động từ mô hình.
 */
public class FaceEmbedder implements AutoCloseable {
    public static final String MODEL_FILE = "mobilefacenet.tflite";
    /** Số chiều vector mà backend yêu cầu (app.face.dimension). */
    public static final int EXPECTED_DIMENSION = 192;

    private final Interpreter interpreter;
    private final int batch;
    private final int inputSize;
    private final int outputDim;

    public FaceEmbedder(Context context) throws IOException {
        MappedByteBuffer model;
        try (AssetFileDescriptor fd = context.getAssets().openFd(MODEL_FILE);
             FileInputStream in = new FileInputStream(fd.getFileDescriptor());
             FileChannel channel = in.getChannel()) {
            model = channel.map(FileChannel.MapMode.READ_ONLY,
                    fd.getStartOffset(), fd.getDeclaredLength());
        }
        interpreter = new Interpreter(model);
        int[] inShape = interpreter.getInputTensor(0).shape();   // [batch, h, w, 3]
        batch = inShape[0];
        inputSize = inShape[1];
        outputDim = interpreter.getOutputTensor(0).shape()[1];
        if (outputDim != EXPECTED_DIMENSION) {
            interpreter.close();
            throw new IOException("Mô hình xuất vector " + outputDim + " chiều, backend cần "
                    + EXPECTED_DIMENSION + " chiều");
        }
    }

    /** @param face ảnh đã cắt đúng vùng khuôn mặt. Trả về vector đã chuẩn hóa L2. */
    public synchronized float[] embed(Bitmap face) {
        Bitmap scaled = Bitmap.createScaledBitmap(face, inputSize, inputSize, true);
        int[] pixels = new int[inputSize * inputSize];
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize);

        ByteBuffer input = ByteBuffer.allocateDirect(4 * batch * inputSize * inputSize * 3);
        input.order(ByteOrder.nativeOrder());
        for (int b = 0; b < batch; b++) {
            for (int p : pixels) {
                // Cùng cách chuẩn hóa với mã gốc của mô hình: (giá trị - 127.5) / 128, thứ tự RGB
                input.putFloat((((p >> 16) & 0xFF) - 127.5f) / 128f);
                input.putFloat((((p >> 8) & 0xFF) - 127.5f) / 128f);
                input.putFloat(((p & 0xFF) - 127.5f) / 128f);
            }
        }
        input.rewind();

        float[][] output = new float[batch][outputDim];
        interpreter.run(input, output);
        float[] embedding = output[0];
        Embeddings.normalize(embedding);
        return embedding;
    }

    @Override
    public synchronized void close() {
        interpreter.close();
    }
}
