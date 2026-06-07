package com.healthcare.domain.user.repository;

import com.healthcare.domain.user.entity.UserIdentity;
import com.healthcare.domain.user.entity.UserIdentity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {
    Optional<UserIdentity> findByProviderAndProviderSubject(Provider provider, String providerSubject);
}
