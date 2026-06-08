package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {
}
