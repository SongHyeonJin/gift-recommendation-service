package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, UUID> {
}
