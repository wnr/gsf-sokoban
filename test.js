/* jshint node: true, bitwise: false */

'use strict';

//-----------------------------------------------------------------------------
// Dependencies
//-----------------------------------------------------------------------------

var chalk = require('chalk');
var exec = require('child_process').exec;
var fs = require('fs');
var os = require('os');
var ProgessBar = require('progress');

//-----------------------------------------------------------------------------
// Main execution point
//-----------------------------------------------------------------------------

var config = {
  tests: 20,
  timeout: 10*1000
};

config.tests = process.argv[2] || config.tests; //TODO: Fix so that if nothing is inputted, the default should be all tests.
config.timeout = process.argv[3] || config.timeout;

printHeader('Google Search First Sokoban Test');

removeDir('temp', function(err) {
  if(err) { throw err; }

  createDir('temp', function(err) {
    if(err) { throw err; }

    createDir('temp/out.sokoban', function(err) {
      if(err) { throw err; }

      compile(function(err) {
        if(err) { throw err; }

        readTestData('test.data', config.tests, function(err, tests) {
          var tester = new Tester(tests, config.timeout);
          tester.run(function done() {
            tester.printResults();
            tester.printExceptions();
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
function Tester(maps, timeout) {
  if(!Array.isArray(maps)) {
    throw new Error('Maps data required');
  }

  this.maps = maps;
  this.tests = maps.length;
  this.executed = 0;
  this.passed = 0;
  this.failed = 0;
  this.running = 0;
  this.exceptions = [];
  this.elapsed = 0;
  this.cpus = os.cpus().length;
  this.time = Date.now();
  this.timeout = timeout || 0;

  this.bar = new ProgessBar('[:bar] :current/:total (:percent) :elapsed s', {
    width: (this.tests <= 100 ? this.tests : 100) + 1,
    total: this.tests,
    complete: '>',
    incomplete: '='
  });
}

Tester.prototype.run = function(print, cb) {
  if(!cb && typeof print === 'function') {
    cb = print;
    print = null;
  }

  if(!print) {
    print = true;
  }

  if(print) {
    console.log('\n' + chalk.yellow(this.tests + ' tests to be executed by ' + this.cpus + ' cores.') + '\n');
  }

  for(var i = 0; i < this.cpus && i < this.tests; i++) {
    var m = this.maps.shift();
    this.test(m.map, m.level, cb);
  }
};

Tester.prototype.isDone = function() {
  if(this.executed >= this.tests) {
    this.elapsed = Date.now() - this.time;
    return true;
  }

  return false;
};

Tester.prototype.test = function(map, level, cb) {
  this.running++;

  var self = this;

  test(map, this.timeout, function(err, result) {
    self.running--;
    self.executed++;

    if(err || !result) {
      self.failed++;

      if(err) {
        self.exceptions.push({
          test: 'Test ' + level,
          err: err.message === 'Command failed: ' ? 'Timeout' : err.message,
          cmd: 'echo "' + map.replace(/\$/g, '\\$') + '" | java -cp temp/out.sokoban Main'
        });
      }
    }

    if(result && !err) {
      var walker = new Walker(map);

      try {
	if(walker.goByString(result.replace(/\n/g, '')).isSolved()) {
	  self.passed++;
	} else {
	  self.failed++;
	}
      } catch(e) {
        self.failed++;
	self.exceptions.push({
	  test: 'Test ' + level,
	  err: 'Result: ' + result + '\nExceptio:' + e,
	  cmd: 'echo "' + map.replace(/\$/g, '\\$') + '" | java -cp temp/out.sokoban Main'
	});
      }
    }

    self.bar.tick();

    if(self.isDone()) {
      if(cb) {
        cb();
      }
    } else {
      if(self.running < self.cpus && self.executed + self.running < self.tests) {
        var test = self.maps.shift();
        self.test(test.map,  test.level, cb);
      }
    }
  });
};

Tester.prototype.printExceptions = function() {
  console.log('');

  if(this.exceptions.length) {
    printHeader('Exceptions', chalk.red);

    for(var i = 0; i < this.exceptions.length; i++) {
      console.log(chalk.red(this.exceptions[i].test) + '\n' + this.exceptions[i].err + '\n' + chalk.yellow('Test with: ' + this.exceptions[i].cmd));
    }
  }

  console.log('\n');
};

Tester.prototype.printResults = function() {
  console.log('Total:    ' + this.bar.total);
  console.log('Passed:   ' + chalk.green(this.passed.toString()));
  console.log('Failed:   ' + chalk.red(this.failed.toString()));
  console.log('Time:     ' + chalk.yellow((this.elapsed / 1000).toFixed(1) + ' s'));
};

/**
 * Walker
 */
function Walker(map) {
  if(map) {
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
    if(!object || typeof object.indexOf !== 'function') {
      throw new Error('Invalid object to search in.');
    }

    for(var i = 0; i < searches.length; i++) {
      var r = object.indexOf(searches[i]);
      if(r) {
        return r;
      }
    }

    return -1;
  }

  if(typeof map !== 'string') {
    throw new Error('Unsupported map type.');
  }

  var m = map.split('\n').filter(function(e) {
    return e.length > 0;
  }).map(function(e) {
      return e.split('');
    });

  for(var x = 0; x < m.length; x++) {
    var y = indexOfEither(m[x], '@+');

    if(~y) {
      this.player = new this.Position(x, y);
    }
  }

  if(!this.player) {
    throw new Error('Unable to find player in map.');
  }

  return m;
};

Walker.prototype.isSolved = function() {
  for(var i = 0; i < this.map.length; i++) {
    if(~this.map[i].indexOf('@')) {
      return false;
    }
  }

  return true;
};

Walker.prototype.goByString = function(string) {
  if(typeof string !== 'string') {
    throw new Error('Invalid arguments');
  }

  for(var i = 0; i < string.length; i++) {
    if(string[i] === ' ') { continue; }

    this.go(this.player, string[i]);
  }

  return this;
};

Walker.prototype.go = function(from, dir) {
  if(!from || !dir) {
    throw new Error('Invalid arguments.');
  }

  var to = this.dirToPos(from, dir);

  var np = this.map[to.y][to.x];

  if(np === '#') {
    throw new Error('Invalid move.');
  }

  if(np === '$' || np === '*') {
    var to2 = this.dirToPos(to, dir);
    this.moveBox(to, to2);
  } else if(np === ' ') {
    this.map[to.y][to.x] = '@';
  } else if(np === '.') {
    this.map[to.y][to.x] = '+';
  } else {
    throw new Error('Unknown error.');
  }

  if(this.map[from.y][from.x] === '@') {
    this.map[from.y][from.x] = ' ';
  } else {
    this.map[from.y][from.x] = '.';
  }

  this.player.x = to.x;
  this.player.y = to.y;
};

Walker.prototype.dirToPos = function(from, dir) {
  if(!from) {
    throw new Error('Invalid arguments.');
  }

  var n = new this.Position(from.x, from.y);

  if(dir === 'R') {
    n.x++;
  } else if(dir === 'L') {
    n.x--;
  } else if(dir === 'D') {
    n.y++;
  } else if(dir === 'U') {
    n.y--;
  } else {
    throw new Error('Invalid direction.');
  }

  return n;
};

Walker.prototype.moveBox = function(from, to) {
  if(!from || !to) {
    throw new Error('Invalid arguments.');
  }

  var np = this.map[to.y][to.x];

  if(np === ' ') {
    this.map[to.y][to.x] = '$';
  } else if(np === '.') {
    this.map[to.y][to.x] = '*';
  } else {
    throw new Error('Invalid move');
  }

  if(this.map[from.y][from.x] === '$') {
    this.map[from.y][from.x] = ' ';
  } else {
    this.map[from.y][from.x] = '.';
  }
};

//-----------------------------------------------------------------------------
// Helper functions
//-----------------------------------------------------------------------------

function readTestData(file, limit, cb) {
  if(!cb) {
    throw new Error('Callback required.');
  }

  printJob('Reading test data ' + file);
  fs.readFile(file, 'utf8', function(err, data) {
    if(err) {
      printJobFailed();
      cb(err);
      return;
    }

    var result = data.split(/;LEVEL \d+/).splice(1, limit);

    for(var i = 0; i < result.length; i++) {
      result[i] = {
        level: i+1,
        map: result[i].replace(/\r/g, '')
      };
    }

    printJobDone();

    cb(null, result);
  });
}

function printHeader(str, color) {
  var length = 50;

  if(!color) {
    color = chalk.yellow;
  }

  var leftSpace = new Array(length/2 - 1 - Math.floor(str.length/2)).join(' ');
  var rightSpace = new Array(length/2 - leftSpace - Math.ceil(str.length/2)).join(' ');

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
  execute('Compiling', 'javac src/*.java -d temp/out.sokoban -encoding UTF-8', cb);
}

function test(map, timeout, cb) {
  var child = execute('java -cp temp/out.sokoban Main', timeout, cb);
  child.stdin.write(map.replace(/^\n/, '') + ';\n');
}

function execute(job, cmd, timeout, cb) {
  if(!cmd) {
    cmd = job;
    job = null;
  }

  if(typeof cmd === 'function') {
    cb = cmd;
    cmd = job;
    job = null;
  }

  if(typeof cmd === 'number') {
    cb = timeout;
    timeout = cmd;
    cmd = job;
    job = null;
  }

  if(typeof timeout === 'function') {
    cb = timeout;
    timeout = null;
  }

  if(job) {
    printJob(job);
  }

  if(!timeout) {
    timeout = 0;
  }

  var child = exec(cmd, {timeout: timeout}, function(err, stdout, stderr) {
    if(err || stderr) {
      if(job) {
        printJobFailed();
      }

      if(cb) {
        cb(err || new Error(stderr));
        return;
      } else {
        throw err || new Error(stderr);
      }
    }

    if(job) {
      printJobDone();
    }

    if(cb) {
      cb(null, stdout);
    }
  });

  return child;
}
