package com.healthcare.common.notification;

import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FcmConfigTest {

    private final FcmConfig fcmConfig = new FcmConfig();

    @BeforeEach
    @AfterEach
    void cleanUpFirebaseApps() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("mock mode이면 FirebaseApp을 초기화하지 않는다")
    void firebaseApp_mockMode_returnsNull() throws Exception {
        FirebaseApp app = fcmConfig.firebaseApp(new FcmProperties(true, ""));

        assertThat(app).isNull();
    }

    @Test
    @DisplayName("credentials-path가 디렉터리이면 FirebaseApp을 초기화하지 않는다")
    void firebaseApp_credentialsPathDirectory_returnsNull() throws Exception {
        FirebaseApp app = fcmConfig.firebaseApp(new FcmProperties(false, tempDir.toString()));

        assertThat(app).isNull();
    }

    @Test
    @DisplayName("credentials-path가 없는 파일이면 FirebaseApp을 초기화하지 않는다")
    void firebaseApp_credentialsPathMissing_returnsNull() throws Exception {
        Path missingPath = tempDir.resolve("fcm-credentials.json");

        FirebaseApp app = fcmConfig.firebaseApp(new FcmProperties(false, missingPath.toString()));

        assertThat(app).isNull();
    }
}
