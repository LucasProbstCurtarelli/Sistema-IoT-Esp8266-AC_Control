package com.iot.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Singleton row that stores the panel's lamp link map.
 * {@code linksJson} is the JSON object the client saved, for example
 * {@code {"lampada_1":["lampada_2"]}}.
 */
@Entity
@Table(name = "light_link_map")
@Data
@NoArgsConstructor
public class LightLinkMap {

    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "links_json", nullable = false)
    private String linksJson;
}
