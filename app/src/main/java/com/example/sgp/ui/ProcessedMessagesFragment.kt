ackage com.example.sgp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sgp.databinding.FragmentProcessedMessagesBinding
import com.example.sgp.repository.ThreatRepository
import com.example.sgp.ui.adapter.ProcessedMessageAdapter

class ProcessedMessagesFragment : Fragment() {

    private var _binding: FragmentProcessedMessagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var processedMessageAdapter: ProcessedMessageAdapter
    private lateinit var threatRepository: ThreatRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProcessedMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        threatRepository = ThreatRepository.getInstance()
        setupRecyclerView()
        observeProcessedMessages()
        updateStatistics()
    }

    private fun setupRecyclerView() {
        processedMessageAdapter = ProcessedMessageAdapter()
        binding.rvProcessedMessages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = processedMessageAdapter
        }
    }

    private fun observeProcessedMessages() {
        threatRepository.processedMessages.observe(viewLifecycleOwner) { messages ->
            processedMessageAdapter.submitList(messages)
            updateEmptyState(messages.isEmpty())
            updateStatistics()
        }

        // Observe total scanned messages for real-time updates
        threatRepository.totalScannedMessages.observe(viewLifecycleOwner) {
            updateStatistics()
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvProcessedMessages.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun updateStatistics() {
        val totalScanned = threatRepository.getTotalScannedCount()
        val processedCount = threatRepository.getProcessedMessageCount()
        val threatCount = threatRepository.getThreatCount()
        val safeCount = processedCount - threatCount

        binding.tvTotalProcessed.text = processedCount.toString()
        binding.tvSafeMessages.text = safeCount.toString()
        binding.tvThreatMessages.text = threatCount.toString()
        binding.tvTotalScanned.text = totalScanned.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
