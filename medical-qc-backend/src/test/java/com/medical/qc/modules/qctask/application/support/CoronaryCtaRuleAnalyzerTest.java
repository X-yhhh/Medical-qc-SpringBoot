package com.medical.qc.modules.qctask.application.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CoronaryCtaRuleAnalyzerTest {
    private final CoronaryCtaRuleAnalyzer analyzer = new CoronaryCtaRuleAnalyzer();

    @TempDir
    Path tempDir;

    @Test
    void shouldFailHeartRateControlWhenHeartRateTooHigh() throws IOException {
        Path niftiPath = writeTestNifti(tempDir.resolve("cta-high-hr.nii"));
        CoronaryCtaPreparedContext context = new CoronaryCtaPreparedContext(
                "local",
                niftiPath.toString(),
                "王五",
                "CTA-2001",
                "男",
                60,
                LocalDate.of(2026, 3, 15),
                "Philips iCT 256",
                "cta-high-hr.nii",
                82,
                7,
                "",
                "100 kV",
                0.7D);

        Map<String, Object> result = analyzer.analyze(context);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> qcItems = (List<Map<String, Object>>) result.get("qcItems");
        Map<String, Object> heartRateItem = qcItems.stream()
                .filter(item -> "心率控制".equals(item.get("name")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> ecgItem = qcItems.stream()
                .filter(item -> "心电门控".equals(item.get("name")))
                .findFirst()
                .orElseThrow();

        assertEquals("不合格", heartRateItem.get("status"));
        assertNotEquals("合格", ecgItem.get("status"));
    }

    @Test
    void shouldReturnStableSummaryForSameVolume(@TempDir Path localTempDir) throws IOException {
        Path niftiPath = writeTestNifti(localTempDir.resolve("cta-stable.nii"));
        CoronaryCtaPreparedContext context = new CoronaryCtaPreparedContext(
                "local",
                niftiPath.toString(),
                "赵六",
                "CTA-2002",
                "女",
                55,
                LocalDate.of(2026, 3, 15),
                "Philips iCT 256",
                "cta-stable.nii",
                64,
                2,
                "75% (Diastolic)",
                "100 kV",
                0.7D);

        Map<String, Object> first = analyzer.analyze(context);
        Map<String, Object> second = analyzer.analyze(context);

        assertEquals(first.get("qcItems"), second.get("qcItems"));
        assertEquals(first.get("summary"), second.get("summary"));
        assertEquals("coronary_cta_qc_rule_v1", first.get("modelCode"));
    }

    private Path writeTestNifti(Path outputPath) throws IOException {
        int width = 4;
        int height = 4;
        int depth = 4;
        short[] voxels = new short[width * height * depth];
        for (int index = 0; index < voxels.length; index++) {
            voxels[index] = (short) (80 + (index % 6) * 20);
        }
        voxels[0] = 420;
        voxels[5] = 380;
        voxels[10] = 300;
        voxels[63] = 1200;

        ByteBuffer buffer = ByteBuffer.allocate(352 + voxels.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(348);
        buffer.position(40);
        buffer.putShort((short) 3);
        buffer.putShort((short) width);
        buffer.putShort((short) height);
        buffer.putShort((short) depth);
        buffer.putShort((short) 1);
        buffer.position(70);
        buffer.putShort((short) 4);
        buffer.putShort((short) 16);
        buffer.position(76);
        buffer.putFloat(0.0F);
        buffer.putFloat(0.7F);
        buffer.putFloat(0.7F);
        buffer.putFloat(0.7F);
        buffer.position(108);
        buffer.putFloat(352.0F);
        buffer.position(112);
        buffer.putFloat(1.0F);
        buffer.putFloat(0.0F);
        buffer.position(344);
        buffer.put((byte) 'n');
        buffer.put((byte) '+');
        buffer.put((byte) '1');
        buffer.put((byte) 0);
        buffer.position(352);
        for (short voxel : voxels) {
            buffer.putShort(voxel);
        }

        Files.write(outputPath, buffer.array());
        return outputPath;
    }
}
