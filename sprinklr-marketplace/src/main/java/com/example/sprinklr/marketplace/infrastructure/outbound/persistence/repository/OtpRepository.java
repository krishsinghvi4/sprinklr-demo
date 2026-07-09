package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.OtpEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.otp.OtpPurpose;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OtpRepository extends MongoRepository<OtpEntry, String> {

    Optional<OtpEntry> findByEmailAndPurpose(String email, OtpPurpose purpose);

    void deleteByEmailAndPurpose(String email, OtpPurpose purpose);
}
