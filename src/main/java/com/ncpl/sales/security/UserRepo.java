package com.ncpl.sales.security;

import javax.transaction.Transactional;
import javax.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<User, Long>{

	@Query("SELECT u FROM User u WHERE u.username = :username")
	public User findUserByUserName(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u ORDER BY u.id")
    List<User> findAllForUpdate();

    @Query("SELECT COUNT(u) FROM User u WHERE u.enabled = true AND u.role IN ('ADMIN', 'SUPER ADMIN') AND u.id <> :id")
    long countOtherEnabledAdministrators(@Param("id") Long id);

	@Modifying
	@Transactional
	@Query("UPDATE User u SET u.password = :password WHERE u.id = :userId")
	int updatePassword(@Param("userId") Long userId, @Param("password") String password);

}
