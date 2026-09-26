from uxsim import World

# Define the main simulation
# Units are standardized to seconds (s) and meters (m)
W = World(
    name="",    # Scenario name
    deltan=5,   # Simulation aggregation unit delta n
    tmax=1200,  # Total simulation time (s)
    print_mode=1, save_mode=1, show_mode=1,    # Various options
    random_seed=0    # Set the random seed
)

# Define the scenario
## Create nodes
W.addNode(name="orig1", x=0, y=0)  #xy coords are for visualization 
W.addNode(name="orig2", x=0, y=2)
W.addNode(name="merge", x=1, y=1)
W.addNode(name="dest", x=2, y=1)
## Create links between nodes
W.addLink(name="link1", start_node="orig1", end_node="merge",
          length=1000, free_flow_speed=20, number_of_lanes=1)
W.addLink(name="link2", start_node="orig2", end_node="merge", 
          length=1000, free_flow_speed=20, number_of_lanes=1)
W.addLink(name="link3", start_node="merge", end_node="dest", 
          length=1000, free_flow_speed=20, number_of_lanes=1)
## Create OD traffic demand between nodes
W.adddemand(orig="orig1", dest="dest", t_start=0, t_end=1000, flow=0.45)
W.adddemand(orig="orig2", dest="dest", t_start=400, t_end=1000, flow=0.6)

# Run the simulation to the end
W.exec_simulation()

# Print summary of simulation result
W.analyzer.print_simple_stats()
