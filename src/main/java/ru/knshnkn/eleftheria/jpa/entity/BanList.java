package ru.knshnkn.eleftheria.jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class BanList {

    @Id
    private Long id;
    private String chatId;

}