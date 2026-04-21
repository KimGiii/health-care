package com.healthcare.domain.bodymeasurement.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProgressPhotoSignedUrls {
    private String original;
    private String thumbnail150;
    private String thumbnail400;
    private String thumbnail800;
}
