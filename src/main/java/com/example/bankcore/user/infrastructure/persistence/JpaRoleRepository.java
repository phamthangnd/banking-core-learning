package com.example.bankcore.user.infrastructure.persistence;

import com.example.bankcore.user.domain.Role;
import com.example.bankcore.user.domain.RoleRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaRoleRepository implements RoleRepository {

    private final RoleJpaRepository roles;

    public JpaRoleRepository(RoleJpaRepository roles) {
        this.roles = roles;
    }

    @Override
    public Optional<Role> findByName(String name) {
        return roles.findByName(name).map(RoleEntity::toDomain);
    }
}
