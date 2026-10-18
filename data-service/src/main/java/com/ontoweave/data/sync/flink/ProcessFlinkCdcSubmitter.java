package com.ontoweave.data.sync.flink;

import com.ontoweave.data.common.config.SyncProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProcessFlinkCdcSubmitter implements FlinkCdcSubmitter {

    private static final Pattern JOB_ID = Pattern.compile(
            "(?i)(?:job\\s*id|jobid)\\s*[:=]?\\s*([0-9a-f]{32})"
    );
    private static final int TIMEOUT_SECONDS = 180;

    private final SyncProperties properties;

    public ProcessFlinkCdcSubmitter(SyncProperties properties) {
        this.properties = properties;
    }

    @Override
    public SubmitResult submit(PipelineSpec spec) {
        Path tempFile = null;
        try {
            tempFile = tempFile();
            Files.writeString(tempFile, spec.yaml(), StandardCharsets.UTF_8);
            secure(tempFile);
            Process process = new ProcessBuilder(
                    "bash",
                    Path.of(properties.flinkCdcHome(), "bin", "flink-cdc.sh").toString(),
                    "--flink-home",
                    properties.flinkHome(),
                    tempFile.toString()
            ).redirectErrorStream(true).start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new FlinkSubmitException("FLINK_SUBMIT_FAILED", "Flink CDC 提交超时");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new FlinkSubmitException("FLINK_SUBMIT_FAILED", sanitize(output));
            }
            Matcher matcher = JOB_ID.matcher(output);
            return new SubmitResult(
                    matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty(),
                    sanitize(output)
            );
        } catch (IOException exception) {
            throw new FlinkSubmitException("FLINK_UNAVAILABLE", "无法启动 Flink CDC 提交进程");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FlinkSubmitException("FLINK_SUBMIT_FAILED", "Flink CDC 提交被中断");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // The file lives in tmpfs; a failed delete must not mask the submit result.
                }
            }
        }
    }

    private Path tempFile() throws IOException {
        Path directory = Path.of("/dev/shm/ontoweave");
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            directory = Path.of(System.getProperty("java.io.tmpdir"), "ontoweave");
            Files.createDirectories(directory);
        }
        return Files.createTempFile(directory, "run-", ".yaml");
    }

    private void secure(Path file) throws IOException {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems are dev-only fallbacks.
        }
    }

    private String sanitize(String output) {
        String trimmed = output == null ? "" : output.trim();
        if (trimmed.length() > 2000) {
            trimmed = trimmed.substring(trimmed.length() - 2000);
        }
        return trimmed.replaceAll("(?i)password\\s*[:=]\\s*\\S+", "password=***");
    }
}
