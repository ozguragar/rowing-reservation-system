package com.rowingclub.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_settings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AppSetting {

    @Id
    private String settingKey;

    @Column(nullable = false)
    private String settingValue;
}
