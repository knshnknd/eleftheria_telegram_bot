package ru.knshnkn.eleftheria.jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Client {

    @Id
    private String creator_chat_id;

    private String fabric_status;
}
