import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class GenerateBcrypt {
    private GenerateBcrypt() {
    }

    public static void main(String[] args) {
        String password = System.getenv("JOBPILOT_RESET_PASSWORD");
        if (password == null || password.length() < 10) {
            throw new IllegalArgumentException("Password must be provided through JOBPILOT_RESET_PASSWORD and contain at least 10 characters");
        }
        System.out.print(new BCryptPasswordEncoder().encode(password));
    }
}
