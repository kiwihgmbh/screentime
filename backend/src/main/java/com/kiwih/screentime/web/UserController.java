package com.kiwih.screentime.web;

import com.kiwih.screentime.domain.User;
import com.kiwih.screentime.security.CurrentUser;
import com.kiwih.screentime.service.UserService;
import com.kiwih.screentime.web.dto.CreateUserRequest;
import com.kiwih.screentime.web.dto.UpdateUserRequest;
import com.kiwih.screentime.web.dto.UserView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;
    private final CurrentUser currentUser;

    public UserController(UserService users, CurrentUser currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    @Operation(summary = "The accounts in this family")
    @GetMapping
    public List<UserView> list() {
        return users.list().stream().map(UserController::view).toList();
    }

    @Operation(summary = "Create an account")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUserRequest request) {
        return view(users.create(currentUser.require(), request.username(),
                request.password(), request.displayName(), request.role()));
    }

    @Operation(summary = "Change an account. Null fields are left alone.")
    @PutMapping("/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return view(users.update(currentUser.require(), id, request.displayName(),
                request.password(), request.role(), request.active()));
    }

    private static UserView view(User u) {
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(),
                u.getRole(), u.isActive(), u.getCreatedAt());
    }
}
