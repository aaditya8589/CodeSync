import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class MaximumSubarray {
    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        int n = Integer.parseInt(in.readLine().trim());
        StringTokenizer tokens = new StringTokenizer(in.readLine());
        long best = 0, current = 0;
        for (int i = 0; i < n; i++) {
            long x = Long.parseLong(tokens.nextToken());
            current = i == 0 ? x : Math.max(x, current + x);
            best = i == 0 ? current : Math.max(best, current);
        }
        System.out.println(best);
    }
}
