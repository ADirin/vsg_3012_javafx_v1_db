FROM eclipse-temurin:21-jdk

WORKDIR /app

# 1. Install GUI libraries
RUN apt-get update && apt-get install -y \
    libx11-6 libxext6 libxrender1 libxtst6 libxi6 libgtk-3-0 mesa-utils wget unzip \
    && rm -rf /var/lib/apt/lists/*

# 3. Download JavaFX SDK
RUN mkdir -p /javafx-sdk \
    && wget -O javafx.zip https://download2.gluonhq.com/openjfx/21/openjfx-21_linux-x64_bin-sdk.zip \
    && unzip javafx.zip -d /javafx-sdk \
    && mv /javafx-sdk/javafx-sdk-21/lib /javafx-sdk/lib \
    && rm -rf /javafx-sdk/javafx-sdk-21 javafx.zip

# Copy fat JAR
COPY target/svg_3012_l10n_rtl.jar app.jar

# Set DISPLAY for Windows (Xming)
ENV DISPLAY=host.docker.internal:0.0

# Run JavaFX app
CMD ["java", \
     "--module-path", "/javafx-sdk/lib", \
     "--add-modules", "javafx.controls,javafx.fxml", \
     "-Dprism.order=sw", \
     "-Dprism.verbose=true", \
     "-jar", "app.jar"]

 # how to run
 # mvn clean package
 # docker build -t amirdirin/svg_3012_rtl .
 # docker run --rm -e DISPLAY=host.docker.internal:0.0 amirdirin/svg_3012_rtl