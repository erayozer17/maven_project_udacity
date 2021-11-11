package org.example.catpoint.image.service;

import java.awt.image.BufferedImage;

public interface ImageService {
    boolean imageContainsCat(BufferedImage image, float confidenceThreshhold);
}
