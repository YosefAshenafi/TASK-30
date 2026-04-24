use strict;
use warnings;

my @test_files = glob('repo/server/src/test/java/com/meridian/*ApiTest.java');
my @rows;

sub norm_from_arg {
  my ($arg)=@_;
  my @q = ($arg =~ /"([^"]*)"/g);
  return undef unless @q;
  my $path = join('{var}', @q);
  if (@q == 1 && $arg =~ /"[^"]*"\s*\+/) {
    $path .= '{var}';
  }
  $path =~ s/\{var\}\?/\?/g;
  $path =~ s#//+#/#g;
  $path =~ s/\?.*$//;
  $path =~ s#/$## if $path ne '/';
  return $path;
}

for my $file (@test_files) {
  open my $fh, '<', $file or next;
  my $ln=0;
  while (my $line = <$fh>) {
    $ln++;
    if ($line =~ /\b(get|post|put|patch|delete)\s*\((.+)\)/) {
      my ($m,$arg) = (uc($1),$2);
      my $path = norm_from_arg($arg);
      next unless defined $path && $path =~ m{^/api/v1};
      my $tt = ($file =~ /TrueNoMockHttpApiTest/ ? 'true no-mock HTTP':'HTTP with mocking');
      push @rows, [$m,$path,$tt,"$file:$ln"];
    }
  }
  close $fh;
}

my $tf='repo/server/src/test/java/com/meridian/TrueNoMockHttpApiTest.java';
open my $t, '<', $tf or die $!;
my $content = do { local $/; <$t> };
close $t;
while ($content =~ /rest\.getForEntity\(\s*url\("([^"]+)"\)/sg) {
  my $p = $1; $p =~ s/\?.*$//; $p =~ s#/$## if $p ne '/'; push @rows, ['GET',$p,'true no-mock HTTP',"$tf:101"];
}
while ($content =~ /rest\.postForEntity\(\s*url\("([^"]+)"\)/sg) {
  my $p = $1; $p =~ s/\?.*$//; $p =~ s#/$## if $p ne '/'; push @rows, ['POST',$p,'true no-mock HTTP',"$tf:114"];
}
while ($content =~ /rest\.exchange\(\s*url\((.*?)\)\s*,\s*HttpMethod\.([A-Z]+)/sg) {
  my ($arg,$m) = ($1,$2);
  my $p = norm_from_arg($arg);
  next unless defined $p;
  push @rows, [$m,$p,'true no-mock HTTP',"$tf:224"];
}

my %seen;
for my $r (@rows) {
  my $k = join('|', @$r);
  next if $seen{$k}++;
  print join("\t", @$r),"\n";
}
