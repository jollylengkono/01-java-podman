package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class UserController {

    private final UserRepository repo;

    public UserController(UserRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/users")
    public List<User> getUsers() {
        return repo.findAll();
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> usersTable() {
        List<User> users = repo.findAll();

        StringBuilder rows = new StringBuilder();
        for (User u : users) {
            rows.append("<tr><td>").append(u.getId())
                .append("</td><td>").append(u.getName())
                .append("</td><td>").append(u.getEmail())
                .append("</td></tr>");
        }

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <title>Users</title>
                  <style>
                    body { font-family: sans-serif; padding: 2rem; background: #f5f5f5; }
                    h1   { font-size: 1.4rem; margin-bottom: 1rem; }
                    table { border-collapse: collapse; background: #fff;
                            box-shadow: 0 1px 3px rgba(0,0,0,.15); }
                    th   { background: #3a7bd5; color: #fff; text-align: left;
                           padding: .6rem 1.2rem; }
                    td   { padding: .5rem 1.2rem; border-bottom: 1px solid #e0e0e0; }
                    tr:last-child td { border-bottom: none; }
                    tr:hover td { background: #f0f4ff; }
                  </style>
                </head>
                <body>
                  <h1>Users</h1>
                  <table>
                    <thead><tr><th>ID</th><th>Name</th><th>Email</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                </body>
                </html>
                """.formatted(rows);

        return ResponseEntity.ok(html);
    }
}
