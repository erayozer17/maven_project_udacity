module security {
    requires java.desktop;
    requires com.google.gson;
    requires java.prefs;
    requires com.google.common;
    requires image;
    exports org.example.catpoint.security.data to app;
    exports org.example.catpoint.security.service to app;
    exports org.example.catpoint.security.application to app;
}