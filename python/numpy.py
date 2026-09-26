import numpy as np

# Array creation
a = np.array([[1, 2], [3, 4]])
b = np.zeros((2, 3))
c = np.ones((3, 2))
d = np.eye(3)
e = np.full((2, 2), 7)
f = np.arange(0, 10, 2)
g = np.linspace(0, 1, 5)

# Array properties
print("Shape:", a.shape)
print("Size:", a.size)
print("Data type:", a.dtype)

# Indexing and slicing
print("Element at (1,1):", a[1, 1])
print("First row:", a[0])
print("Column slice:", a[:, 1])

# Boolean indexing
mask = a > 2
print("Elements > 2:", a[mask])

# Broadcasting
x = np.array([1, 2, 3])
y = np.array([[10], [20], [30]])
broadcast_result = x + y
print("Broadcasted addition:\n", broadcast_result)

# Vectorized operations
arr = np.array([1, 2, 3, 4])
print("Squared:", arr**2)
print("Exponential:", np.exp(arr))

# Aggregation
print("Sum:", arr.sum())
print("Mean:", arr.mean())
print("Standard deviation:", arr.std())
print("Max:", arr.max())
print("Argmax:", arr.argmax())

# Reshape and transpose
reshaped = arr.reshape((2, 2))
transposed = reshaped.T
print("Reshaped:\n", reshaped)
print("Transposed:\n", transposed)

# Stacking and splitting
stacked = np.vstack([x, x])
split = np.hsplit(stacked, 3)
print("Stacked:\n", stacked)
print("Split:", split)

# Linear algebra
A = np.array([[1, 2], [3, 4]])
B = np.array([[2, 0], [1, 2]])
dot_product = np.dot(A, B)
inv_A = np.linalg.inv(A)
eigvals, eigvecs = np.linalg.eig(A)
print("Dot product:\n", dot_product)
print("Inverse:\n", inv_A)
print("Eigenvalues:", eigvals)

# Random sampling
rand_arr = np.random.rand(3, 3)
rand_ints = np.random.randint(0, 10, size=(2, 2))
normal_dist = np.random.normal(loc=0, scale=1, size=1000)
print("Random array:\n", rand_arr)
print("Random integers:\n", rand_ints)
print("Normal distribution mean:", normal_dist.mean())

# Set operations
set1 = np.array([1, 2, 3, 4])
set2 = np.array([3, 4, 5, 6])
print("Union:", np.union1d(set1, set2))
print("Intersection:", np.intersect1d(set1, set2))
print("Set difference:", np.setdiff1d(set1, set2))

# NaN and inf handling
nan_arr = np.array([1, np.nan, 3, np.inf])
print("Is NaN:", np.isnan(nan_arr))
print("Is finite:", np.isfinite(nan_arr))
print("Nanmean:", np.nanmean(nan_arr))
