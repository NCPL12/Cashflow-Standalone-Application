package com.ncpl.sales.security;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService{

	@Autowired
	private UserRepo userRepo;
	@Autowired
	EncryptedPasswordUtils encryptPasswd;
	@Autowired
	private com.ncpl.sales.repository.RolePermissionRepo rolePermissionRepo;

    @PersistenceContext
    private EntityManager entityManager;

	//validating user using database
		@Override
		public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
			System.out.println("Loading User Obj by UserName: " + username);
			User user = userRepo.findUserByUserName(username);

			if (user == null) {
				throw new UsernameNotFoundException("User not found: " + username);
			}

			Set<GrantedAuthority> grantedAuthorities = new HashSet<>();
			if (user.getRole() != null) {
				grantedAuthorities.add(new SimpleGrantedAuthority(user.getRole()));   // KEEP THIS â€” every existing hasAnyAuthority(role name) check still works

				for (com.ncpl.sales.model.RolePermission p : rolePermissionRepo.findByRoleName(user.getRole())) {
					if (p.isCanView())   grantedAuthorities.add(new SimpleGrantedAuthority(p.getModule() + "_VIEW"));
					if (p.isCanEdit())   grantedAuthorities.add(new SimpleGrantedAuthority(p.getModule() + "_EDIT"));
					if (p.isCanDelete()) grantedAuthorities.add(new SimpleGrantedAuthority(p.getModule() + "_DELETE"));
				}
			}

			return new org.springframework.security.core.userdetails.User(
					user.getUsername(),
					user.getPassword(),
					user.isEnabled(),   // enabled
					true,                // accountNonExpired
					true,                // credentialsNonExpired
					true,                // accountNonLocked
					grantedAuthorities);
		}

		/** One user by id, used by the User Management admin page. */
		public User getUserByUserId(Long userId) {
			if (userId == null || userId <= 0) {
				return null;
			}
			return userRepo.findById(userId).orElse(null);
		}

		/** Save a new user (with encrypted password). */
		public User saveUser(User user) {
			if (user.getPassword() != null && !user.getPassword().isEmpty()) {
				user.setPassword(encryptPasswd.encrytePassword(user.getPassword()));
			}
			return userRepo.save(user);
		}

    @Transactional
    public User updateUser(User updates) {
        User user = findUserForUpdate(updates.getId());
        checkAdministratorRemoval(user, updates.isEnabled(), updates.getRole());
        user.setUsername(updates.getUsername());
        user.setRole(updates.getRole());
        user.setNumber(updates.getNumber());
        user.setEmailId(updates.getEmailId());
        user.setEnabled(updates.isEnabled());
        user.setName(updates.getName());
        return userRepo.save(user);
    }

    @Transactional
    public User setUserEnabled(Long userId, boolean enabled) {
        User user = findUserForUpdate(userId);
        checkAdministratorRemoval(user, enabled, user.getRole());
        user.setEnabled(enabled);
        return userRepo.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        if (userId != null && userId > 0) {
            User user = findUserForUpdate(userId);
            checkAdministratorRemoval(user, false, null);
            userRepo.delete(user);
        }
    }

    private User findUserForUpdate(Long userId) {
        // Lock in ID order so concurrent removals cannot each rely on the other administrator.
        User user = userRepo.findAllForUpdate().stream().filter(candidate -> candidate.getId().equals(userId))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("User not found."));
        // A web request may already have loaded an older copy before these locks were acquired.
        entityManager.refresh(user);
        return user;
    }

    private void checkAdministratorRemoval(User user, boolean enabled, String role) {
        if (user.isEnabled() && isAdministrator(user.getRole()) && !(enabled && isAdministrator(role))
                && userRepo.countOtherEnabledAdministrators(user.getId()) == 0) {
            throw new IllegalArgumentException("Keep at least one enabled administrator.");
        }
    }

    private static boolean isAdministrator(String role) {
        return "ADMIN".equals(role) || "SUPER ADMIN".equals(role);
    }

		public boolean userExists(String username) {
			return userRepo.findUserByUserName(username) != null;
		}

		public boolean changePassword(Long userId, String password) {
			User user = userRepo.findById(userId).orElse(null);
			if (user == null) {
				return false;
			}
			String encryptedPassword = encryptPasswd.encrytePassword(password);
			int updatedRows = userRepo.updatePassword(userId, encryptedPassword);
			return updatedRows > 0;
		}


	public User findByUserName(String userName) {
		return userRepo.findUserByUserName(userName);
	}
	
public List<User> getAllUsers() {
    return userRepo.findAll();
}
	
	public User getCurrentUser() {
		User user = null;
		try {
			String userName = SecurityContextHolder.getContext().getAuthentication().getName();
			user = findByUserName(userName);
		} catch (Exception e) {
			System.out.println("Error getting current user: " + e.getMessage());
		}
		return user;
	}
}
