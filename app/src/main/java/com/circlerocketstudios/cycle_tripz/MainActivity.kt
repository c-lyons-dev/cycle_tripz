class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationClient: FusedLocationProviderClient
    private var isTracking = false
    private var currentGroupId: String? = null

    // Firebase
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val database: FirebaseDatabase by lazy { Firebase.database }
    private var groupListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        locationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        binding.btnToggleTracking.setOnClickListener { toggleTracking() }
        binding.btnCreateGroup.setOnClickListener { createGroup() }
        binding.btnJoinGroup.setOnClickListener { showJoinDialog() }
        binding.btnLeaveGroup.setOnClickListener { leaveGroup() }
        updateUIState()
    }

    private fun toggleTracking() {
        isTracking = if (!isTracking) {
            startLocationUpdates()
            true
        } else {
            stopLocationUpdates()
            false
        }
        updateUIState()
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
            val request = LocationRequest.create().apply {
                interval = 3000
                fastestInterval = 1500
                priority = Priority.PRIORITY_HIGH_ACCURACY
            }

            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            startService(Intent(this, LocationService::class.java))
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val speedMph = (location.speed * 2.23694).roundToInt()
                binding.tvSpeed.text = getString(R.string.speed_format, speedMph)
                updateGroupSpeed(speedMph.toDouble())
            }
        }
    }

    private fun updateGroupSpeed(speed: Double) {
        currentGroupId?.let { groupId ->
            database.getReference("groups/$groupId/members/${auth.uid}")
                .setValue(speed)
        }
    }

    // Group Management
    private fun createGroup() {
        val groupId = database.reference.child("groups").push().key!!
        val group = mapOf(
            "name" to "Group ${Random.nextInt(1000, 9999)}",
            "owner" to auth.uid,
            "createdAt" to ServerValue.TIMESTAMP
        )

        database.reference.child("groups/$groupId").setValue(group)
            .addOnSuccessListener {
                currentGroupId = groupId
                setupGroupListener()
                updateUIState()
            }
    }

    private fun setupGroupListener() {
        currentGroupId?.let { groupId ->
            groupListener = database.getReference("groups/$groupId/members")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val totalSpeed = snapshot.children.sumOf { it.getValue(Double::class.java) ?: 0.0 }
                        binding.tvGroupSpeed.text = getString(R.string.group_speed_format, totalSpeed.roundToInt())
                    }

                    override fun onCancelled(error: DatabaseError) {
                        showError("Group update failed: ${error.message}")
                    }
                })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        groupListener?.let { currentGroupId?.let { id ->
            database.getReference("groups/$id").removeEventListener(it)
        }}
        stopLocationUpdates()
    }
}