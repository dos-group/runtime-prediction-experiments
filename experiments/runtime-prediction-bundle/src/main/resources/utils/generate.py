from collections import OrderedDict

from pyhocon import ConfigFactory
from pyhocon.tool import HOCONConverter


def linear_scale_func(x, m=4, n=0):
    return m*(x+1)+n

class WallyConfBuilder(object):
    def __init__(self, pattern, parallelism, memory, scale_func=linear_scale_func):
        self.pattern = pattern
        self.parallelism = parallelism
        self.memory = memory
        self.scale_func = scale_func
        self.includes = []
        self.excludes = []

    def include(self, start, stop):
        self.includes.append(range(start, stop+1))
        return self

    def exclude(self, start, stop):
        self.excludes.append(range(start, stop+1))
        return self

    def _scale_out_dict(self, slaves):
        d = OrderedDict()

        d['total'] = OrderedDict()
        d['total']['hosts'] = len(slaves)
        d['total']['parallelism'] = len(slaves)*self.parallelism
        d['total']['memory'] = len(slaves)*self.memory

        d['hosts'] = slaves

        return d

    def build(self):
        include_set = reduce(set.union, self.includes, set())
        include_set = reduce(set.difference, self.excludes, include_set)
        slaves = map(lambda d: self.pattern % d, include_set)
        slaves = sorted(list(slaves))
        masters = [slaves[0]]
        slaves = slaves[1:]

        d = OrderedDict()
        d['env'] = OrderedDict()
        d['env']['masters'] = masters
        d['env']['per-node'] = OrderedDict()
        d['env']['per-node']['parallelism'] = self.parallelism
        d['env']['per-node']['memory'] = self.memory

        d['env']['slaves'] = OrderedDict()
        d['env']['slaves']['all'] = self._scale_out_dict(slaves)

        # compute the scale outs
        tops = []
        i = 0
        while True:
            top = self.scale_func(i)
            if top > len(slaves):
                break
            tops.append(top)
            i = i + 1

        for top in tops:
            d['env']['slaves']['top%03d'%top] = self._scale_out_dict(slaves[:top])

        return d



if __name__ == '__main__':

    d = WallyConfBuilder('wally%03d', 8, 16408060) \
        .include(59, 79) \
        .include(91, 130) \
        .build()

    conf = ConfigFactory.from_dict(d)
    print HOCONConverter.to_hocon(conf)
