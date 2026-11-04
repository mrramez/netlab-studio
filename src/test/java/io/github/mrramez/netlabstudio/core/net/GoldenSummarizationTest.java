package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The golden vectors of docs/CURRICULUM.md section 4.4 (route summarization). */
class GoldenSummarizationTest {

  private static List<Ipv4Network> range(String prefixBefore, int from, int to, String suffix) {
    List<Ipv4Network> networks = new ArrayList<>();
    for (int i = from; i <= to; i++) {
      networks.add(Ipv4Network.parse(prefixBefore + i + suffix));
    }
    return networks;
  }

  @ParameterizedTest(name = "4.4 {0}.{1}{2} to {3} -> {4} ({5})")
  @CsvSource({
    "192.168., 0, .0/24, 3, 192.168.0.0/22, 255.255.252.0",
    "172., 16, .0.0/16, 23, 172.16.0.0/13, 255.248.0.0"
  })
  void contiguousRangesSummariseExactly(
      String head, int from, String tail, int to, String expected, String mask) {
    // The second row counts the second octet from 16 to 23; the first counts the third from 0 to 3.
    List<Ipv4Network> networks =
        head.startsWith("192")
            ? range("192.168.", from, to, ".0/24")
            : range("172.", from, to, ".0.0/16");

    RouteSummary summary = RouteSummarizer.summarize(networks).result();

    assertThat(summary.summary()).hasToString(expected);
    assertThat(summary.summary().mask()).hasToString(mask);
    assertThat(summary.isExact()).isTrue();
  }
}
