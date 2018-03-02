import numpy as np
from scipy.stats import norm
import matplotlib as mpl
import matplotlib.pyplot as plt
import preprocessing as prep
import pdfplot as pdfplt
import kldplot as kldplt

name = "basic-bimodal"
dir = "../../../flip-bench/experiments/basic-bimodal/"

fig = plt.figure(figsize=(20, 5))

def data_loc(i):
    return dir + "basic-bimodal-pdf-" + str(i) + ".out"

def expected(x, i = -1):
    return 0.5 * norm.pdf(x - 2) + 0.5 * norm.pdf(x + 2)


# PDF: Represented

xmin = -5
xmax = 5
ymin = 0
ymax = 0.3

data_counts = [40, 60, 340]

i = 0
for data_count in data_counts:
    ax = plt.subplot(1, len(data_counts) + 1, i + 1)
    annotation = "(" + chr(ord('a') + i) + ")" + " pdf at update count: " + str(data_count)
    ax.set_title(annotation)
    pdfplt.pdfplot_bar(ax, expected, data_loc(data_count), xmin, xmax, ymin, ymax)
    i = i + 1


# KLD

countmin = 40
countmax = 500
rearr_start = 50
rearr_period = 100
kld_max = 0.13

kld_data_loc = dir + "basic-bimodal-kld.out"

ax = plt.subplot(1, len(data_counts) + 1, len(data_counts) + 1)
ax.set_title("(d) KL-divergence")
kldplt.kldplot(ax, kld_data_loc, kld_max, countmin, countmax, rearr_start, rearr_period)

# Save Plot

plt.legend()
plt.savefig(name + '.pdf')
plt.savefig(name + '.png')
plt.show()


# PDF: Animated

start = 10
end = 500
step = 10
fps = 4

utd_animation1 = pdfplt.animated_pdfplot_errorbar(data_loc, start, end, step, expected, xmin, xmax, ymin, ymax)
utd_animation1.save(name + '-errorbar.gif', writer='imagemagick', fps=fps)

utd_animation2 = pdfplt.animated_pdfplot_bar(data_loc, start, end, step, expected, xmin, xmax, ymin, ymax)
utd_animation2.save(name + '-histo.gif', writer='imagemagick', fps=fps)
