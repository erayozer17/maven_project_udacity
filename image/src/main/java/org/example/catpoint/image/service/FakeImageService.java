package org.example.catpoint.image.service;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Service that tries to guess if an image displays a cat.
 */
public class FakeImageService implements ImageService {
    private static final Random RANDOM = new Random();

    public boolean imageContainsCat(BufferedImage image, float confidenceThreshhold) {
        return RANDOM.nextBoolean();
    }
}
