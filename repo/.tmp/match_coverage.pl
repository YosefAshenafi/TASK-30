use strict;
use warnings;

open my $e,'<','repo/.tmp/endpoints76.tsv' or die $!;
my @eps;
while(<$e>){chomp; next unless $_; my ($m,$p)=split /\t/,$_,2; push @eps, [$m,$p];}
close $e;

open my $t,'<','repo/.tmp/test_calls.tsv' or die $!;
my @calls;
while(<$t>){chomp; next unless $_; my ($m,$p,$tt,$ev)=split /\t/,$_,4; push @calls, [$m,$p,$tt,$ev];}
close $t;

for my $ep (@eps){
  my ($em,$epath)=@$ep;
  my $rx = quotemeta($epath);
  $rx =~ s/\\{[^\\}]+\\}/[^\\/]+/g;
  my @m = grep { $_->[0] eq $em && $_->[1] =~ /^$rx$/ } @calls;

  my $covered = @m ? 'yes' : 'no';
  my %files; my @evi; my $has_true=0;
  for my $c (@m){
    $has_true=1 if $c->[2] eq 'true no-mock HTTP';
    my ($file) = split /:/, $c->[3],2;
    $files{$file}=1;
    push @evi, $c->[3];
  }
  my $type = $covered eq 'no' ? 'unit-only / indirect' : ($has_true ? 'true no-mock HTTP' : 'HTTP with mocking');
  my $files = @m ? join(', ', sort keys %files) : 'NONE';
  my $evidence = @m ? join('; ', @evi[0..($#evi>2?2:$#evi)]) : 'NONE';
  print join("\t",$em,$epath,$covered,$type,$files,$evidence),"\n";
}
