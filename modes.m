# copyright nqqero 2019 - offered under the apache-2.0 license

pkg load io

function zz = loadBench(file,varargin)
  files = ifelse(iscellstr(file),file,[{file},varargin]);
  datum = cellfun(@csv2cell,files,'uniformoutput',false);
  csv = cat(1,datum{:});
  headers = regexprep(
    csv(1,:),
    {"Param: ",".* Error .*"},
    {"","error"});
  tmp = cell2struct(csv,headers,2);
  zz = tmp(!cellfun(@ischar,{tmp.Score}));
 endfunction

function name = getName(str)
  name = {strsplit(str,"."){3}};
  endfunction

function names = getNames(raw)
  names = cellfun(@getName,raw);
  endfunction

function str = printg(pre,tmp,obj)
  str = [pre sprintf(tmp,obj)];
  endfunction

function talk(obj)
  if (iscellstr(obj)); printg("cellstr: ","%d ",size(obj))
  elseif (iscell(obj)); printg("cell: ","%d ",size(obj))
  elseif (isstruct(obj));
    printg("struct: ","%d ",size(obj))
    fieldnames(obj)
  elseif (ischar(obj)); printg("string: ","%d ",size(obj))
  elseif (isnumeric(obj)); printg("number: ","%d ",size(obj))
  endif
  endfunction

function res = assa(zz)
  names = fieldnames(zz);
  res = struct();
  for ii = 1:length(names)
    name = names{ii};
    data = {zz.(name)};
    if !iscellstr(data); data = [zz.(name)]; endif
    res.(name) = data;
  endfor
endfunction

function [u,i,j] = cunique(zz)
  names = fieldnames(zz);
  [u,i,j] = deal(struct());
  for ii = 1:length(names)
    name = names{ii};
    data = {zz.(name)};
    if !iscellstr(data); data = [zz.(name)]; endif
    [u.(name) i.(name) j.(name)] = unique(data');
  endfor
endfunction

# bin the input according to the benchmark and mode param
function [a,u,v] = slurp(zz);
  [u,i,j] = cunique(zz);
  shape = [length(u.Benchmark) length(u.mode)];
  ka = sub2ind(shape,j.Benchmark,j.mode);
  total = accumarray(ka,[zz.Score],shape);
  num = accumarray(ka,1,shape);
  nz = num(:) > 0;
  dev = a = zeros(shape);
  a(nz) = total(nz) ./ num(nz);
  sq = accumarray(ka,[zz.Score] .^ 2,shape);
  dev(nz) = ((sq(nz)./num(nz) - a(nz) .^ 2) .^ .5) ./ a(nz);
  v.ka = ka;
  v.dev = dev;
  v.a = a;
  v.u = u;
  v.score = [zz.Score];
  v.data = zz;
  v.ax = ax = a ./ max(a);
  v.names = getNames(u.Benchmark);
  v.pretty = [100*[ax sum(ax,2)] (1:size(ax,1))'];
  v.ag = ag = mean(ax,2);
  [~,v.kr] = sort(ag);
endfunction

# bin the input according to the benchmark and all the params, not just mode
function [a,u,ka] = slurpOpt(zz);
  [u,i,j] = cunique(zz);
  a = zeros(length(u.size),length(u.soft),length(u.sleep),length(u.Benchmark),length(u.numHash));
  ka = sub2ind(size(a),j.size,j.soft,j.sleep,j.Benchmark,j.numHash);
  a(ka) = [zz.Score];
endfunction



function nd = nd(av)
  nd = floor(100*std(av,0,2)./mean(av,2));
endfunction

function [am,nd] = modeDev(ax)
  ma = ax(:,2:3);
  md = ax(:,6:9);
  nd = [nd(ma) nd(md) ];
  am = [ ax(:,1) mean(ma,2) ax(:,4:5) mean(md,2) ];
endfunction

# multi dimensional and averaged aggregates
function [va vm] = mergeBench(varargin)
  v = [varargin{:}];
  va.a = cat(3,v.a);
  va.ax = cat(3,v.ax);
  va.pretty = cat(3,v.pretty);

  vm.a = mean(va.a,3);
  vm.ax = mean(va.ax,3);
  vm.pretty = mean(va.pretty,3);
  vm.ag = mean(vm.ax,2);
  vm.names = v(1).names;
  [~,vm.kr] = sort(vm.ag);
  vm.kg = vm.kr(end:-1:1);
endfunction

function v = filterBench(v,k)
  v.a = v.a(k,:);
  v.ax = v.ax(k,:);
  v.ag = v.ag(k);
  v.pretty = v.pretty(k,:);
  v.names = v.names(k);
endfunction


# modes
# _, throughput - many small tasks, no latency, no limit
# a, heavy lifting - a few (100-200) bigger tasks, no latency, no limit
# b, waiting - efficiency of a few small tasks with latency, no limit
# c, mixed load - many small tasks and a few random big ones, no latency, no limit
# d/e, back pressure - many small tasks, with limit, induced latency

# task size: big, small, mixed
# latency vs no latency vs soft limit (induced latency)
# efficiency vs throughput


function shiftHandle(handle,x,y)
    pos = get(handle,"position");
    pos += [x y 0 0];
    set(handle,"position",pos);
    endfunction



function plotPerf(ag,baseline,names)
colormap(jet()*.7);
perf = 100*ag/baseline;
bar(perf);
set(gca,'XTickLabel',names);
axis([0 length(perf)+1 min(perf)-20 max(perf)+5]);
ylabel("percent ops/s relative to a for-each loop");
title("Composite Performance Relative to a For-Each Loop, Higher is Better");
leg = legend(strcat(...
    "mean performance of throughput, \n", ...
    "waiting, heavy lifting, mixed load, \n", ...
    "and back pressure use-cases, \n", ...
    "normalized by single threaded \n", ...
    "for-loop performance"),"location","northwest");
shiftHandle(leg,.02,-.02);
endfunction

function plotDetails(ax,names,modes)
colormap(hsv(5)*.7);
hax = bar(100*ax);
set(gca,'XTickLabel',names);
axis([0 size(ax,1)+1 axis()(3:4)]);
leg = legend(modes,"location","southeast");
shiftHandle(leg,-.04,.02);
ylabel("normalized ops/s");
title("Normalized Per Use-Case Performance, Higher is Better");
endfunction

function use = getActors()
use = strsplit(
  "Stream8 RxJava Direct Conversant QuasarFair JctoolsFair ForkJoin Kilim");
endfunction


function vs = printBench(paths,doPrint)
if (nargin==0); paths = "saved/*"; endif
vs = pipeBench(getActors(),paths);

if (nargin >= 2 && !doPrint); return; endif

clf
plotPerf(vs.ag,vs.baseline,vs.bench);
print doc/bench.jpg

clf
plotDetails(vs.ax,vs.bench,vs.modes);
print doc/details.jpg
endfunction

function [vs,km] = sortBench(vm,actors)
va = filterBench(vm,actors);
[~,ks] = sort(va.ag);
vs = filterBench(va,ks);
km = actors(ks);
endfunction

# if paths is a string, do a listing of it
# otherwise, treat as a list of file names
function vs = pipeBench(use,paths)
if (ischar(paths)); paths = cellstr(ls(paths)); endif
vv = cellfun(@(x) [~,~,ans] = slurp(loadBench(x)), paths);
[~,vm] = mergeBench(vv(:));
actors = cellfun(@(x) find(strcmp(vm.names,x),1),use);
[vs,vs.map] = sortBench(vm,actors);

vs.single = find(strcmp(vs.names,"Direct"));
vs.bench = names = regexprep(vs.names,
  ostrsplit("Fair ForkList Direct"," "),
  ostrsplit(" ForkJoin For-Each"," "));
vs.modes = strrep(vv(1).u.mode',
  {"all", "burn", "cost", "delay", "fast"},
  {"heavy lifting", "waiting", "mixed load", "back pressure", "throughput"});
vs.baseline = vs.ag(vs.single);
vs.use = use;
vs.paths = paths;
vs.raw = vv;
vs.merge = vm;
endfunction

