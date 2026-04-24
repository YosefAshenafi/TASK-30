use strict;
use warnings;
my @files = glob('repo/server/src/main/java/com/meridian/**/*Controller.java');
push @files, 'repo/server/src/main/java/com/meridian/HealthController.java';
my %seen;
for my $file (@files){
  open my $fh,'<',$file or next;
  my $base='';
  my $ln=0;
  while(my $line=<$fh>){
    $ln++;
    if($line =~ /\@RequestMapping\("([^"]*)"\)/){$base=$1;}
    if($line =~ /\@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)(\("([^"]*)"\))?/){
      my $m=$1; $m =~ s/Mapping//; $m = uc($m);
      my $p=defined($3)?$3:'';
      my $full = $base.$p; $full =~ s#//+#/#g; $full =~ s#/$## if $full ne '/';
      my $k="$m\t$full";
      next if $seen{$k}++;
      print "$m\t$full\t$file:$ln\n";
    }
  }
  close $fh;
}
