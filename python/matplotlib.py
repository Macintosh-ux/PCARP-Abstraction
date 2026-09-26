import matplotlib.pyplot as plt
import numpy as np
from matplotlib.gridspec import GridSpec
from matplotlib.ticker import MultipleLocator, FuncFormatter
from matplotlib.colors import LogNorm
from matplotlib import cm

# Create sample data
x = np.linspace(0, 10, 100)
y = np.sin(x)
z = np.random.rand(10, 10)

# Line plot with labels, legend, and grid
fig1, ax1 = plt.subplots()
ax1.plot(x, y, label='sin(x)', color='blue', linestyle='--', linewidth=2)
ax1.set_title("Line Plot")
ax1.set_xlabel("x-axis")
ax1.set_ylabel("y-axis")
ax1.legend()
ax1.grid(True)

# Scatter plot with color mapping and size variation
fig2, ax2 = plt.subplots()
colors = np.random.rand(100)
sizes = 100 * np.random.rand(100)
ax2.scatter(x, y, c=colors, s=sizes, alpha=0.5, cmap='viridis')
ax2.set_title("Scatter Plot")

# Bar plot with custom ticks and formatting
fig3, ax3 = plt.subplots()
categories = ['A', 'B', 'C', 'D']
values = [10, 24, 36, 18]
bars = ax3.bar(categories, values, color='skyblue')
ax3.set_title("Bar Plot")
ax3.yaxis.set_major_locator(MultipleLocator(10))
ax3.yaxis.set_major_formatter(FuncFormatter(lambda val, pos: f'{val}%'))

# Histogram with density and bins
data = np.random.randn(1000)
fig4, ax4 = plt.subplots()
ax4.hist(data, bins=30, density=True, alpha=0.7, color='green')
ax4.set_title("Histogram")

# Heatmap with colorbar and normalization
fig5, ax5 = plt.subplots()
cax = ax5.imshow(z, interpolation='nearest', cmap=cm.inferno, norm=LogNorm())
fig5.colorbar(cax)
ax5.set_title("Heatmap")

# Subplots with GridSpec layout
fig6 = plt.figure(constrained_layout=True)
gs = GridSpec(2, 2, figure=fig6)
ax6_1 = fig6.add_subplot(gs[0, 0])
ax6_2 = fig6.add_subplot(gs[0, 1])
ax6_3 = fig6.add_subplot(gs[1, :])
ax6_1.plot(x, np.cos(x), color='red')
ax6_2.plot(x, np.tan(x), color='purple')
ax6_3.plot(x, np.exp(-x), color='black')
ax6_1.set_title("Cosine")
ax6_2.set_title("Tangent")
ax6_3.set_title("Exponential Decay")

# Polar plot
fig7, ax7 = plt.subplots(subplot_kw={'projection': 'polar'})
theta = np.linspace(0, 2 * np.pi, 100)
r = np.abs(np.sin(5 * theta))
ax7.plot(theta, r)
ax7.set_title("Polar Plot")

# 3D plot
from mpl_toolkits.mplot3d import Axes3D
fig8 = plt.figure()
ax8 = fig8.add_subplot(111, projection='3d')
X, Y = np.meshgrid(np.linspace(-5, 5, 50), np.linspace(-5, 5, 50))
Z = np.sin(np.sqrt(X**2 + Y**2))
ax8.plot_surface(X, Y, Z, cmap='coolwarm')
ax8.set_title("3D Surface Plot")

# Styles and context management
with plt.style.context('ggplot'):
    fig9, ax9 = plt.subplots()
    ax9.plot(x, np.log1p(x), label='log(1+x)')
    ax9.set_title("Styled Plot with ggplot")
    ax9.legend()
