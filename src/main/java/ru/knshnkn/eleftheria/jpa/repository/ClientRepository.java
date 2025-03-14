package ru.knshnkn.eleftheria.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.knshnkn.eleftheria.jpa.entity.Client;

public interface ClientRepository extends JpaRepository<Client, String> {
}