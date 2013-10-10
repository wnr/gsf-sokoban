/* jshint node: true, bitwise: false */

'use strict';

//-----------------------------------------------------------------------------
// Dependencies
//-----------------------------------------------------------------------------

var chalk = require('chalk');
var exec = require('child_process').exec;
var spawn = require('child_process').spawn;
var fs = require('fs');
var os = require('os');
var ProgessBar = require('progress');
var path = require('path');

//-----------------------------------------------------------------------------
// Process handling
//-----------------------------------------------------------------------------
var children = {};

process.on('SIGINT', function() {
  console.log('');

  for (var index in children) {
    if (children.hasOwnProperty(index)) {
      var child = children[index].child;
      console.log('killing child ' + child.pid);
      child.kill();
    }
  }

  process.exit();
});

//-----------------------------------------------------------------------------
// Main execution point
//-----------------------------------------------------------------------------

var config = {
  tests: Infinity,
  file: 'test.data',
  timeout: 11 * 1000,
  cores: os.cpus().length
};

if (process.argv[2] !== undefined) {
  if (parseInt(process.argv[2], 10)) {
    config.tests = parseInt(process.argv[2], 10);
  } else {
    config.file = process.argv[2];
  }
}

if (process.argv[3] !== undefined) {
  config.timeout = parseInt(process.argv[3], 10) * 1000;
}

if (process.argv[4] !== undefined) {
  config.cores = parseInt(process.argv[4], 10);
}

printHeader('Google Search First Sokoban Test');

removeDir('temp', function(err) {
  if (err) {
    throw err;
  }

  createDir('temp', function(err) {
    if (err) {
      throw err;
    }

    createDir(path.normalize('temp/out.sokoban'), function(err) {
      if (err) {
        throw err;
      }

      compile(function(err) {
        if (err) {
          throw err;
        }

        readTestData('samples.data', function(err, samples) {
          if (err) {
            throw err;
          }

          var tester = new Tester(samples, config.timeout, true, config.cores);

          printJob('Testing samples');
          tester.run(function done() {
            if (tester.failed > 0) {
              printJobFailed();
              tester.printExceptions(true);
            } else {
              printJobDone();

              if (config.tests === 0) {
                return;
              }

              readTestData(config.file, config.tests, function(err, tests) {
                if (err) {
                  throw err;
                }

                var tester = new Tester(tests, config.timeout, false, config.cores);
                tester.run(function done() {
                  tester.printResults();
                  tester.printPassed();
                  tester.printTimeouts();
                  tester.printVisual();
                  tester.printExceptions();

                  var analyzer = new Analyzer(tester.exceptions, config.cores);
                  analyzer.run(function done(err) {
                    if (err) throw err;

                    analyzer.printResults();
                  });
                });
              });
            }
          });
        });
      });
    });
  });
});

//-----------------------------------------------------------------------------
// Classes
//-----------------------------------------------------------------------------

/**
 * Tester
 */

function Tester(maps, timeout, silent, cpus) {
  if (!Array.isArray(maps)) {
    throw new Error('Maps data required');
  }

  this.maps = maps;
  this.tests = maps.length;
  this.executed = 0;
  this.passed = [];
  this.failed = 0;
  this.running = 0;
  this.exceptions = [];
  this.cpus = cpus || 1;
  this.timeout = timeout || 0;
  this.silent = silent || false;

  if (!silent) {
    this.bar = new ProgessBar('[:bar] :current/:total (:percent)', {
      width: (this.tests <= 100 ? this.tests : 100) + 1,
      total: this.tests,
      complete: '>',
      incomplete: '='
    });
  }
}

Tester.prototype.run = function(cb) {
  if (!this.silent) {
    console.log('\n' + chalk.yellow(this.tests + ' tests to be executed by ' + this.cpus + ' core' + (this.cpus === 1 ? '' : 's') + '.') + '\n');
  }

  for (var i = 0; i < this.cpus && i < this.tests; i++) {
    var m = this.maps.shift();
    this.test(m.map, m.level, cb);
  }
};

Tester.prototype.isDone = function() {
  if (this.executed >= this.tests) {
    return true;
  }

  return false;
};

Tester.prototype.test = function(map, level, cb) {
  this.running++;

  var self = this;

  test(map, this.timeout, function(err, result, time) {
    self.running--;
    self.executed++;

    if (err || !result) {
      self.failed++;

      if (err) {
        self.exceptions.push({
          level: level,
          test: 'Test ' + level,
          err: err.message === 'timeout' ? 'Timeout' : err.message,
          cmd: 'echo "' + map.replace(/\$/g, '\\$') + '" | java -cp temp/out.sokoban Main',
          timeout: err.message === 'timeout',
          map: map
        });
      }
    }

    if (result && !err) {
      var walker = new Walker(map);

      try {
        if (walker.goByString(result.replace(/\n|\r/g, '')).isSolved()) {
          self.passed.push(new self.TestResult(level, time));
        } else {
          self.failed++;
        }
      } catch (e) {
        self.failed++;
        self.exceptions.push({
          level: level,
          test: 'Test ' + level,
          err: 'Result: ' + result + '\nException: ' + e,
          cmd: 'echo "' + map.replace(/\$/g, '\\$') + '" | java -cp temp/out.sokoban Main',
          map: map
        });
      }
    }

    if (self.bar) {
      self.bar.tick();
    }

    if (self.isDone()) {
      if (cb) {
        cb();
      }
    } else {
      if (self.running < self.cpus && self.executed + self.running < self.tests) {
        var test = self.maps.shift();
        self.test(test.map, test.level, cb);
      }
    }
  });
};

Tester.prototype.printExceptions = function(cmd, timeouts) {
  if (!cmd) {
    cmd = false;
  }

  console.log('');

  var e = this.exceptions;

  if (!timeouts) {
    e = e.filter(function(o) {
      return !o.timeout;
    });
  }

  if (e.length) {
    printHeader('Exceptions', chalk.red);

    for (var i = 0; i < e.length; i++) {
      console.log(chalk.red(e[i].test) + '\n' + e[i].err + (cmd ? '\n' + chalk.yellow('Test with: ' + e[i].cmd) : ''));
    }
  }
};

Tester.prototype.printResults = function() {
  var passedTime = 0;

  for (var i = 0; i < this.passed.length; i++) {
    if (this.passed[i].time) {
      passedTime += this.passed[i].time;
    }
  }

  var average = this.passed.length > 0 && passedTime / this.passed.length;
  var median = getMedian(this.passed.map(function(e) {
    return e.time;
  })) || 0;

  console.log('');
  console.log('Total:    ' + this.bar.total);
  console.log('Passed:   ' + chalk.green(this.passed.length.toString() + (passedTime > 0 ? ' (' + passedTime / 1000 + ' s)' : '')));
  console.log('Failed:   ' + chalk.red(this.failed.toString()));
  console.log('');
  console.log('Average passed time: ' + (average / 1000).toFixed(2) + ' s');
  console.log('Median passed time:  ' + (median / 1000).toFixed(2) + ' s');
};

Tester.prototype.printPassed = function() {
  var p = this.passed.sort(function(a, b) {
    if (a.level < b.level) {
      return -1;
    } else if (a.level > b.level) {
      return 1;
    }

    return 0;
  });

  var str = '\nPassed:';

  for (var i = 0; i < p.length; i++) {
    str += '\n' + p[i].level + (p[i].time ? '\t  (' + p[i].time / 1000 + ' s)' : '') + ' ';
  }

  console.log(chalk.green(str));
};

Tester.prototype.printVisual = function() {
  var visuals = [];

  for (var i = 0; i < this.passed.length; i++) {
    visuals.push({
      level: this.passed[i].level,
      passed: true
    });
  }

  for (i = 0; i < this.exceptions.length; i++) {
    visuals.push({
      level: this.exceptions[i].level,
      passed: false,
      timeout: this.exceptions[i].timeout
    });
  }

  visuals = visuals.sort(function(a, b) {
    if (a.level < b.level) {
      return -1;
    } else if (a.level > b.level) {
      return 1;
    }

    return 0;
  });

  var str = '\n';

  for (i = 0; i < visuals.length; i++) {
    if (visuals[i].passed) {
      str += chalk.bgGreen.black(' ' + visuals[i].level + ' ');
    } else {
      str += chalk.bgRed.black(' ' + visuals[i].level + ' ');
    }
  }

  console.log(str);
};

Tester.prototype.printTimeouts = function() {
  var timeouts = this.exceptions.filter(function(o) {
    return o.timeout;
  }).sort(function(a, b) {
    if (a.level < b.level) {
      return -1;
    } else if (a.level > b.level) {
      return 1;
    }

    return 0;
  });

  var str = '\nTimeouts (' + this.timeout / 1000 + ' s):\n';

  for (var i = 0; i < timeouts.length; i++) {
    str += timeouts[i].level + ' ';
  }

  console.log(chalk.red(str));
};

Tester.prototype.TestResult = function(level, time) {
  this.level = level;
  this.time = time;
};

/**
 * Walker
 */

function Walker(map) {
  if (map) {
    this.map = this.parseMap(map);
  }
}

Walker.prototype.Position = function(x, y) {
  return {
    x: x,
    y: y
  };
};

Walker.prototype.parseMap = function(map) {
  function indexOfEither(object, searches) {
    if (!object || typeof object.indexOf !== 'function') {
      throw new Error('Invalid object to search in.');
    }

    for (var i = 0; i < searches.length; i++) {
      var r = object.indexOf(searches[i]);
      if (~r) {
        return r;
      }
    }

    return -1;
  }

  if (typeof map !== 'string') {
    throw new Error('Unsupported map type.');
  }

  var m = map.split('\n').filter(function(e) {
    return e.length > 0;
  }).map(function(e) {
    return e.split('');
  });

  for (var y = 0; y < m.length; y++) {
    var x = indexOfEither(m[y], '@+');

    if (~x) {
      this.player = new this.Position(x, y);
    }
  }

  if (!this.player) {
    throw new Error('Unable to find player in map:\n' + map);
  }

  return m;
};

Walker.prototype.isSolved = function() {
  for (var i = 0; i < this.map.length; i++) {
    if (~this.map[i].indexOf('@')) {
      return false;
    }
  }

  return true;
};

Walker.prototype.goByString = function(string) {
  if (typeof string !== 'string') {
    throw new Error('Invalid arguments');
  }

  for (var i = 0; i < string.length; i++) {
    if (string[i] === ' ') {
      continue;
    }

    this.go(this.player, string[i]);
  }

  return this;
};

Walker.prototype.go = function(from, dir) {
  if (!from || !dir) {
    throw new Error('Invalid arguments.');
  }

  var to = this.dirToPos(from, dir);

  var np = this.map[to.y][to.x];

  if (np === '#') {
    throw new Error('Invalid move.');
  }

  if (np === '$' || np === '*') {
    var to2 = this.dirToPos(to, dir);
    this.moveBox(to, to2);
  } else if (np === ' ') {
    this.map[to.y][to.x] = '@';
  } else if (np === '.') {
    this.map[to.y][to.x] = '+';
  } else {
    throw new Error('Unknown error.');
  }

  if (this.map[from.y][from.x] === '@') {
    this.map[from.y][from.x] = ' ';
  } else {
    this.map[from.y][from.x] = '.';
  }

  this.player.x = to.x;
  this.player.y = to.y;
};

Walker.prototype.dirToPos = function(from, dir) {
  if (!from) {
    throw new Error('Invalid arguments.');
  }

  var n = new this.Position(from.x, from.y);

  if (dir === 'R') {
    n.x++;
  } else if (dir === 'L') {
    n.x--;
  } else if (dir === 'D') {
    n.y++;
  } else if (dir === 'U') {
    n.y--;
  } else {
    throw new Error('Invalid direction.');
  }

  return n;
};

Walker.prototype.moveBox = function(from, to) {
  if (!from || !to) {
    throw new Error('Invalid arguments.');
  }

  var np = this.map[to.y][to.x];

  if (np === ' ') {
    this.map[to.y][to.x] = '$';
  } else if (np === '.') {
    this.map[to.y][to.x] = '*';
  } else {
    throw new Error('Invalid move');
  }

  if (this.map[from.y][from.x] === '$') {
    this.map[from.y][from.x] = ' ';
  } else {
    this.map[from.y][from.x] = '.';
  }
};

/**
 * Analyzer
 */

function Analyzer(tests, cpus) {
  this.tests = this.parseTests(tests);
  this.total = this.tests.length;
  this.results = [];
  this.analyzed = 0;
  this.running = 0;
  this.cpus = cpus || 1;
}

Analyzer.prototype.parseTests = function(tests) {
  return tests.map(function(test) {
    var result = test;

    if (!test.level) {
      throw new Error('Level required.');
    }

    if (!test.map) {
      throw new Error('Map required.');
    }

    if (!test.time) {
      result.time = config.timeout;
    }

    return result;
  });
};

Analyzer.prototype.run = function(cb) {
  console.log('\n' + chalk.yellow(this.total + ' tests to be analyzed by ' + this.cpus + ' core' + (this.cpus === 1 ? '' : 's') + '.') + '\n');

  for (var i = 0; i < this.cpus && i < this.total; i++) {
    var t = this.tests.shift();
    this.analyze(t, cb);
  }
};

Analyzer.prototype.isDone = function() {
  if (this.analyzed >= this.total) {
    return true;
  }

  return false;
};

Analyzer.prototype.analyze = function(test, cb) {
  this.running++;

  var self = this;

  getMapInfo(test.map, function(err, result) {
    if (err || !result) {
      throw err || new Error('no result');
    }

    self.running--;
    self.analyzed++;

    test.info = result;
    self.results.push(test);

    if (self.isDone()) {
      cb(null);
    } else {
      if (self.running < self.cpus && self.analyzed + self.running < self.total) {
        var t = self.tests.shift();
        self.analyze(t, cb);
      }
    }
  });
};

Analyzer.prototype.computeData = function() {
  var self = this;

  function calcMedian(prop) {
    return getMedian(self.results.map(function(r) {
      return r.info[prop];
    }));
  }

  var data = {
    average: {
      free: 0,
      walls: 0,
      boxes: 0,
      density: {
        boxes: 0
      }
    },
    median: {
      free: 0,
      walls: 0,
      boxes: 0,
      density: {
        boxes: 0
      }
    }
  };

  this.results.forEach(function(r) {
    var info = r.info;

    for (var prop in data.average) {
      if (data.average.hasOwnProperty(prop) && info.hasOwnProperty(prop)) {
        data.average[prop] += info[prop];
      }
    }
  });

  for (var prop in data.average) {
    if (data.average.hasOwnProperty(prop)) {
      if (typeof data.average[prop] === 'number') {
        data.average[prop] /= this.total;
      }
    }
  }

  for (prop in data.median) {
    if (data.median.hasOwnProperty(prop) && typeof data.median[prop] === 'number') {
      data.median[prop] = calcMedian(prop);
    }
  }

  data.average.density.boxes += data.average.boxes / data.average.free;
  data.median.density.boxes = data.median.boxes / data.median.free;

  return data;
};

Analyzer.prototype.printResults = function() {
  var data = this.computeData();

  var prop, objprop;

  for (prop in data.average) {
    if (data.average.hasOwnProperty(prop)) {
      if (typeof data.average[prop] === 'object') {
        for (objprop in data.average[prop]) {
          if (data.average[prop].hasOwnProperty(objprop)) {
            console.log(chalk.blue('average ' + objprop + ' ' + prop + ':\t' + formatPercent(data.average[prop][objprop])));
          }
        }
      } else {
        console.log(chalk.blue('average ' + prop + ':\t\t' + data.average[prop]));
      }
    }
  }

  for (prop in data.median) {
    if (data.median.hasOwnProperty(prop)) {
      if (typeof data.median[prop] === 'object') {
        for (objprop in data.median[prop]) {
          if (data.median[prop].hasOwnProperty(objprop)) {
            console.log(chalk.yellow('median ' + objprop + ' ' + prop + ':\t' + formatPercent(data.median[prop][objprop])));
          }
        }
      } else {
        console.log(chalk.yellow('median ' + prop + ':\t\t' + data.median[prop]));
      }
    }
  }
};

//-----------------------------------------------------------------------------
// Helper functions
//-----------------------------------------------------------------------------

function formatPercent(value) {
  return (value * 100).toFixed(2) + ' %';
}

function getMedian(array) {
  function comparer(a, b) {
    if (a < b) {
      return -1;
    } else if (a > b) {
      return 1;
    }

    return 0;
  }

  var values = array.sort(comparer);

  return values.length % 2 === 1 ? values[Math.floor(values.length / 2)] : (values[Math.floor(values.length / 2)] + values[Math.ceil(values.length / 2)]) / 2;
}

function getMapInfo(map, cb) {
  function parseValue(string, value) {
    var regex = new RegExp(value + ':\\s\\d+');
    return parseInt(string.match(regex)[0].replace(/[^\d]/g, ''), 10);
  }

  execute('echo "' + map.replace(/\$/g, '\\$') + '" | java -cp temp/out.sokoban Info', function(err, stdout, stderr) {
    if (err || stderr || !stdout) {
      cb(err || new Error(stderr));
      return;
    }

    cb(null, {
      free: parseValue(stdout, 'free'),
      walls: parseValue(stdout, 'walls'),
      boxes: parseValue(stdout, 'boxes'),
      trapping: parseValue(stdout, 'trapping'),
      tunnels: parseValue(stdout, 'tunnels'),
      deadends: parseValue(stdout, 'deadends'),
      roomcells: parseValue(stdout, 'roomcells'),
      openings: parseValue(stdout, 'openings')
    });
  });
}

function readTestData(file, limit, cb) {
  if (typeof limit === 'function') {
    cb = limit;
    limit = Infinity;
  }

  if (!cb) {
    throw new Error('Callback required.');
  }

  printJob('Reading test data ' + file);
  fs.readFile(file, 'utf8', function(err, data) {
    if (err) {
      printJobFailed();
      cb(err);
      return;
    }

    var result = data.split(/;LEVEL \d+/).splice(1, limit);
    var levels = data.match(/\d+/g);

    for (var i = 0; i < result.length; i++) {
      result[i] = {
        level: levels[i],
        map: result[i].replace(/\r/g, '')
      };
    }

    printJobDone();

    cb(null, result);
  });
}

function printHeader(str, color) {
  var length = 50;

  if (!color) {
    color = chalk.yellow;
  }

  var leftSpace = new Array(length / 2 - 1 - Math.floor(str.length / 2)).join(' ');
  var rightSpace = new Array(length / 2 - leftSpace - Math.ceil(str.length / 2)).join(' ');

  console.log('\n' + color(new Array(length).join('#')));
  console.log(color('#') + leftSpace + str + rightSpace + color('#'));
  console.log(color(new Array(50).join('#')) + '\n');
}

function printJob(str) {
  var length = 44;

  str += '...';

  process.stdout.write(str + new Array(length - str.length).join(' '));
}

function printJobDone() {
  process.stdout.write('[' + chalk.green('done') + ']\n');
}

function printJobFailed() {
  process.stdout.write('[' + chalk.red('failed') + ']\n');
}

function removeDir(dir, cb) {
  execute('Removing directory ' + dir, 'rm -rf ' + dir, cb);
}

function createDir(dir, cb) {
  execute('Creating directory ' + dir, 'mkdir ' + dir, cb);
}

function compile(cb) {
  var src = path.normalize('src/');
  execute('Compiling', 'javac ' + src + 'Main.java ' + src + 'BoardState.java ' + src + 'BoardUtil.java ' + src + 'Info.java -d temp/out.sokoban -encoding UTF-8', cb);
}

function test(map, timeout, cb) {
  function clearTimer(child) {
    var obj = children[child.pid];
    var elapsed = Date.now() - obj.time;
    clearTimeout(obj.timer);
    delete children[child.pid];
    return elapsed;
  }

  var stdout = '';
  var stderr = '';
  var timedout = false;

  var child = spawn('java', ['-cp', 'temp/out.sokoban', 'Main'], {
    detached: true
  });

  child.stdin.write(map.replace(/^\n/, '') + (map.match(/\n$/) === null ? '\n' : '') + ';\n');

  child.stdout.on('data', function(data) {
    stdout += data;
  });

  child.stderr.on('data', function(data) {
    stderr += data;
  });

  child.on('close', function(code) {
    var elapsed = clearTimer(child);

    if (timedout) {
      cb(new Error('timeout'));
      return;
    }

    if (code === 0) {
      if (stderr || !stdout) {
        cb(new Error(stderr));
      } else {
        cb(null, stdout, elapsed);
      }
    } else {
      cb(new Error(stderr));
    }
  });

  var timer = setTimeout(function() {
    timedout = true;
    child.kill();
  }, timeout);

  children[child.pid] = {
    timer: timer,
    time: Date.now(),
    child: child
  };
}

function execute(job, cmd, timeout, cb) {
  if (!cmd) {
    cmd = job;
    job = null;
  }

  if (typeof cmd === 'function') {
    cb = cmd;
    cmd = job;
    job = null;
  }

  if (typeof cmd === 'number') {
    cb = timeout;
    timeout = cmd;
    cmd = job;
    job = null;
  }

  if (typeof timeout === 'function') {
    cb = timeout;
    timeout = null;
  }

  if (job) {
    printJob(job);
  }

  if (!timeout) {
    timeout = 0;
  }

  var child = exec(cmd, {
    timeout: timeout
  }, function(err, stdout, stderr) {
    if (err || stderr) {
      if (job) {
        printJobFailed();
      }

      if (cb) {
        cb(err || new Error(stderr));
        return;
      } else {
        throw err || new Error(stderr);
      }
    }

    if (job) {
      printJobDone();
    }

    if (cb) {
      cb(null, stdout);
    }
  });

  return child;
}