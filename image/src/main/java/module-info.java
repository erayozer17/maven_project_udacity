module image {
    exports org.example.catpoint.image.service to security, app;
    requires software.amazon.awssdk.auth;
    requires software.amazon.awssdk.core;
    requires software.amazon.awssdk.regions;
    requires software.amazon.awssdk.services.rekognition;
    requires slf4j.api;
    requires java.desktop;
}