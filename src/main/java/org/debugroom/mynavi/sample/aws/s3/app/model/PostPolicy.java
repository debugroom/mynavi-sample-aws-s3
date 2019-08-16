package org.debugroom.mynavi.sample.aws.s3.app.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PostPolicy implements Serializable {

    private String expiration;
    private String[][] conditions;

}
