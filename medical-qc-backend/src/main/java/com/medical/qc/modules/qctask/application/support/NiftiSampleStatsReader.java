package com.medical.qc.modules.qctask.application.support;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * 读取 NIfTI 头和少量体素样本，用于规则分析阶段的轻量统计。
 */
public final class NiftiSampleStatsReader {
    private static final int HEADER_SIZE = 348;
    private static final int DEFAULT_MAX_SAMPLES = 50_000;

    private NiftiSampleStatsReader() {
    }

    public static NiftiSampleStats read(Path filePath) throws IOException {
        if (filePath == null || !Files.exists(filePath)) {
            throw new IOException("NIfTI 文件不存在");
        }

        try (InputStream rawStream = Files.newInputStream(filePath);
             InputStream inputStream = createInputStream(filePath, rawStream)) {
            byte[] headerBytes = readFully(inputStream, HEADER_SIZE);
            ByteOrder byteOrder = resolveByteOrder(headerBytes);
            ByteBuffer header = ByteBuffer.wrap(headerBytes).order(byteOrder);

            int width = readShort(header, 42);
            int height = readShort(header, 44);
            int depth = readShort(header, 46);
            int datatype = readShort(header, 70);
            int bitpix = readShort(header, 72);
            double spacingX = readFloat(header, 80);
            double spacingY = readFloat(header, 84);
            double spacingZ = readFloat(header, 88);
            double slope = readFloat(header, 112);
            double intercept = readFloat(header, 116);
            if (Math.abs(slope) < 1e-8D) {
                slope = 1.0D;
            }

            int voxOffset = Math.max(352, (int) Math.floor(readFloat(header, 108)));
            skipFully(inputStream, Math.max(0, voxOffset - HEADER_SIZE));

            long voxelCount = (long) width * height * depth;
            if (width <= 0 || height <= 0 || depth <= 0 || voxelCount <= 0L) {
                throw new IOException("NIfTI 维度非法");
            }
            int bytesPerVoxel = Math.max(1, bitpix / 8);
            long sampleStride = Math.max(1L, voxelCount / DEFAULT_MAX_SAMPLES);

            double mean = 0.0D;
            double m2 = 0.0D;
            long sampleCount = 0L;
            double maxHu = Double.NEGATIVE_INFINITY;
            long highDensityCount = 0L;
            double[] sliceSums = new double[depth];
            long[] sliceCounts = new long[depth];

            byte[] voxelBuffer = new byte[bytesPerVoxel];
            long sliceVoxelCount = (long) width * height;
            for (long voxelIndex = 0; voxelIndex < voxelCount; voxelIndex++) {
                readFully(inputStream, voxelBuffer, 0, bytesPerVoxel);
                if (voxelIndex % sampleStride != 0L) {
                    continue;
                }

                double huValue = convertVoxelValue(voxelBuffer, datatype, byteOrder) * slope + intercept;
                sampleCount += 1L;
                double delta = huValue - mean;
                mean += delta / sampleCount;
                m2 += delta * (huValue - mean);
                maxHu = Math.max(maxHu, huValue);
                if (huValue >= 250.0D) {
                    highDensityCount += 1L;
                }

                int sliceIndex = (int) Math.min(depth - 1L, voxelIndex / sliceVoxelCount);
                sliceSums[sliceIndex] += huValue;
                sliceCounts[sliceIndex] += 1L;
            }

            List<Double> sampledSliceMeans = new ArrayList<>();
            for (int index = 0; index < depth; index++) {
                if (sliceCounts[index] > 0L) {
                    sampledSliceMeans.add(sliceSums[index] / sliceCounts[index]);
                }
            }

            double meanSliceShift = 0.0D;
            if (sampledSliceMeans.size() > 1) {
                double shiftSum = 0.0D;
                for (int index = 1; index < sampledSliceMeans.size(); index++) {
                    shiftSum += Math.abs(sampledSliceMeans.get(index) - sampledSliceMeans.get(index - 1));
                }
                meanSliceShift = shiftSum / (sampledSliceMeans.size() - 1);
            }

            double variance = sampleCount > 1L ? m2 / (sampleCount - 1L) : 0.0D;
            return new NiftiSampleStats(
                    width,
                    height,
                    depth,
                    spacingX,
                    spacingY,
                    spacingZ,
                    sampleCount,
                    round(mean),
                    round(Math.sqrt(Math.max(variance, 0.0D))),
                    round(maxHu == Double.NEGATIVE_INFINITY ? 0.0D : maxHu),
                    round(sampleCount == 0L ? 0.0D : (double) highDensityCount / sampleCount),
                    round(meanSliceShift));
        }
    }

    private static InputStream createInputStream(Path filePath, InputStream rawStream) throws IOException {
        String filename = filePath.getFileName() == null ? "" : filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        InputStream bufferedStream = new BufferedInputStream(rawStream);
        if (filename.endsWith(".nii.gz")) {
            return new BufferedInputStream(new GZIPInputStream(bufferedStream));
        }
        return bufferedStream;
    }

    private static ByteOrder resolveByteOrder(byte[] headerBytes) throws IOException {
        ByteBuffer littleEndian = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        if (littleEndian.getInt(0) == HEADER_SIZE) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        ByteBuffer bigEndian = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN);
        if (bigEndian.getInt(0) == HEADER_SIZE) {
            return ByteOrder.BIG_ENDIAN;
        }
        throw new IOException("无法识别 NIfTI 字节序");
    }

    private static byte[] readFully(InputStream inputStream, int length) throws IOException {
        byte[] bytes = new byte[length];
        readFully(inputStream, bytes, 0, length);
        return bytes;
    }

    private static void readFully(InputStream inputStream, byte[] buffer, int offset, int length) throws IOException {
        int remaining = length;
        int cursor = offset;
        while (remaining > 0) {
            int read = inputStream.read(buffer, cursor, remaining);
            if (read < 0) {
                throw new EOFException("读取 NIfTI 体素数据时提前结束");
            }
            cursor += read;
            remaining -= read;
        }
    }

    private static void skipFully(InputStream inputStream, long length) throws IOException {
        long remaining = length;
        while (remaining > 0L) {
            long skipped = inputStream.skip(remaining);
            if (skipped <= 0L) {
                if (inputStream.read() < 0) {
                    throw new EOFException("跳过 NIfTI 头部扩展区时提前结束");
                }
                skipped = 1L;
            }
            remaining -= skipped;
        }
    }

    private static int readShort(ByteBuffer buffer, int offset) {
        return buffer.getShort(offset);
    }

    private static double readFloat(ByteBuffer buffer, int offset) {
        return buffer.getFloat(offset);
    }

    private static double convertVoxelValue(byte[] bytes, int datatype, ByteOrder order) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(order);
        return switch (datatype) {
            case 2 -> Byte.toUnsignedInt(bytes[0]);
            case 4 -> buffer.getShort();
            case 8 -> buffer.getInt();
            case 16 -> buffer.getFloat();
            default -> throw new IOException("暂不支持的 NIfTI datatype: " + datatype);
        };
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
