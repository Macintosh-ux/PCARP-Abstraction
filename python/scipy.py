import numpy as np
from scipy import optimize, integrate, interpolate, fft, linalg, spatial, stats

# Optimization: minimize a function
def func(x):
    return (x - 3)**2 + 2

result_min = optimize.minimize(func, x0=0)
print("Optimization result:", result_min.x)

# Root finding
def root_func(x):
    return x**3 - 2*x - 5

root_result = optimize.root(root_func, x0=2)
print("Root found:", root_result.x)

# Integration: definite integral
def integrand(x):
    return np.exp(-x**2)

integral_result, error = integrate.quad(integrand, 0, 2)
print("Integral result:", integral_result)

# ODE solving
def ode_system(t, y):
    return -2 * y

ode_result = integrate.solve_ivp(ode_system, [0, 5], [1.0])
print("ODE solution at final time:", ode_result.y[:, -1])

# Interpolation
x = np.linspace(0, 10, 10)
y = np.sin(x)
interp_func = interpolate.interp1d(x, y, kind='cubic')
print("Interpolated value at x=5.5:", interp_func(5.5))

# FFT
signal = np.sin(2 * np.pi * 1 * np.linspace(0, 1, 100))
fft_result = fft.fft(signal)
print("FFT first component:", fft_result[0])

# Linear algebra
A = np.array([[3, 2], [1, 4]])
b = np.array([6, 8])
x_sol = linalg.solve(A, b)
print("Linear system solution:", x_sol)

eigvals, eigvecs = linalg.eig(A)
print("Eigenvalues:", eigvals)

# Spatial: KDTree and distance
points = np.random.rand(10, 2)
tree = spatial.KDTree(points)
dist, idx = tree.query([0.5, 0.5])
print("Nearest point index:", idx, "Distance:", dist)

# Stats: distributions and hypothesis testing
normal_samples = stats.norm.rvs(loc=0, scale=1, size=1000)
mean, std = stats.norm.fit(normal_samples)
print("Fitted mean and std:", mean, std)

t_stat, p_val = stats.ttest_1samp(normal_samples, popmean=0)
print("T-test p-value:", p_val)

# Correlation and descriptive stats
x_data = np.random.rand(100)
y_data = x_data + np.random.normal(0, 0.1, 100)
corr_coef, _ = stats.pearsonr(x_data, y_data)
print("Pearson correlation:", corr_coef)
